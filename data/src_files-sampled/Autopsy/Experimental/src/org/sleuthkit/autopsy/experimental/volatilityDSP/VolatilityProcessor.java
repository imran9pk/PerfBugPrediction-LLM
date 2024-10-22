package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.EncodingType;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

class VolatilityProcessor {
    
    private static final Logger logger = Logger.getLogger(VolatilityProcessor.class.getName());
    private static final String VOLATILITY = "Volatility"; private static final String VOLATILITY_EXECUTABLE = "volatility_2.6_win64_standalone.exe"; private final List<String> errorMsgs = new ArrayList<>();
    private final String memoryImagePath;
    private final Image dataSource;
    private final List<String> pluginsToRun;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private File executableFile;
    private String moduleOutputPath;
    private FileManager fileManager;
    private volatile boolean isCancelled;
    private Content outputVirtDir;
    private String profile;
    private Blackboard blackboard;
    private String caseDirectory;

    VolatilityProcessor(String memoryImagePath, Image dataSource, String profile, List<String> pluginsToRun, DataSourceProcessorProgressMonitor progressMonitor) {
        this.profile = profile;
        this.memoryImagePath = memoryImagePath;
        this.pluginsToRun = pluginsToRun;
        this.dataSource = dataSource;
        this.progressMonitor = progressMonitor;
    }

    @NbBundle.Messages({
        "VolatilityProcessor_progressMessage_noCurrentCase=Failed to get current case",
        "VolatilityProcessor_exceptionMessage_volatilityExeNotFound=Volatility executable not found",
        "# {0} - plugin name",
        "VolatilityProcessor_progressMessage_runningImageInfo=Running {0} plugin"
    })
    void run() throws VolatilityProcessorException {
        this.errorMsgs.clear();
        Case currentCase;
        try {

            currentCase = Case.getCurrentCaseThrows();

        } catch (NoCurrentCaseException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_progressMessage_noCurrentCase(), ex);
        }
        blackboard = currentCase.getSleuthkitCase().getBlackboard();
        caseDirectory = currentCase.getCaseDirectory();
        executableFile = locateVolatilityExecutable();
        if (executableFile == null) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_volatilityExeNotFound());
        }

        fileManager = currentCase.getServices().getFileManager();

        try {
            outputVirtDir = currentCase.getSleuthkitCase().addVirtualDirectory(dataSource.getId(), "ModuleOutput");
        } catch (TskCoreException ex) {
            throw new VolatilityProcessorException("Error creating virtual directory", ex);
        }

        Long dataSourceId = dataSource.getId();
        moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), VOLATILITY, dataSourceId.toString()).toString();
        File directory = new File(String.valueOf(moduleOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();
        }

        if (profile.isEmpty()) {
            progressMonitor.setProgressText(Bundle.VolatilityProcessor_progressMessage_runningImageInfo("imageinfo")); runVolatilityPlugin("imageinfo"); profile = getProfileFromImageInfoOutput();
        }

        progressMonitor.setIndeterminate(false);
        progressMonitor.setProgressMax(pluginsToRun.size());
        for (int i = 0; i < pluginsToRun.size(); i++) {
            if (isCancelled) {
                break;
            }
            String pluginToRun = pluginsToRun.get(i);
            runVolatilityPlugin(pluginToRun);
            progressMonitor.setProgress(i);
        }
    }

    List<String> getErrorMessages() {
        return new ArrayList<>(errorMsgs);
    }

    @NbBundle.Messages({
        "VolatilityProcessor_exceptionMessage_failedToRunVolatilityExe=Could not run Volatility",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorRunningPlugin=Volatility error running {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorAddingOutput=Failed to add output for {0} to case",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_searchServiceNotFound=Keyword search service not found, output for {0} plugin not indexed",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorIndexingOutput=Error indexing output for {0} plugin"
    })
    private void runVolatilityPlugin(String pluginToRun) throws VolatilityProcessorException {
        progressMonitor.setProgressText("Running module " + pluginToRun);

        List<String> commandLine = new ArrayList<>();
        commandLine.add("\"" + executableFile + "\""); File memoryImage = new File(memoryImagePath);
        commandLine.add("--filename=" + memoryImage.getName()); if (!profile.isEmpty()) {
            commandLine.add("--profile=" + profile); }
        commandLine.add(pluginToRun);

        switch (pluginToRun) {
            case "dlldump":
            case "moddump":
            case "procdump":
            case "dumpregistry":
            case "dumpfiles":
                String outputDir = moduleOutputPath + File.separator + pluginToRun;
                File directory = new File(outputDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                commandLine.add("--dump-dir=" + outputDir); break;
            default:
                break;
        }

        String outputFileAsString = moduleOutputPath + File.separator + pluginToRun + ".txt"; ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); File outputFile = new File(outputFileAsString);
        processBuilder.redirectOutput(outputFile);
        processBuilder.redirectError(new File(moduleOutputPath + File.separator + "Volatility_err.txt"));  processBuilder.directory(new File(memoryImage.getParent()));

        try {
            int exitVal = ExecUtil.execute(processBuilder);
            if (exitVal != 0) {
                errorMsgs.add(Bundle.VolatilityProcessor_exceptionMessage_errorRunningPlugin(pluginToRun));
                return;
            }
        } catch (IOException | SecurityException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToRunVolatilityExe(), ex);
        }

        if (isCancelled) {
            return;
        }

        try {
            String relativePath = new File(caseDirectory).toURI().relativize(new File(outputFileAsString).toURI()).getPath();
            fileManager.addDerivedFile(pluginToRun, relativePath, outputFile.length(), 0, 0, 0, 0, true, outputVirtDir, null, null, null, null, EncodingType.NONE);
        } catch (TskCoreException ex) {
            errorMsgs.add("Error adding " + pluginToRun + " volatility report as a file");
            logger.log(Level.WARNING, "Error adding report as derived file", ex);
        }

        createArtifactsFromPluginOutput(pluginToRun, new File(outputFileAsString));
    }

    private static File locateVolatilityExecutable() {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName = Paths.get(VOLATILITY, VOLATILITY_EXECUTABLE).toString();
        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, VolatilityProcessor.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }

    @NbBundle.Messages({
        "VolatilityProcessor_exceptionMessage_failedToParseImageInfo=Could not parse image info"
    })
    private String getProfileFromImageInfoOutput() throws VolatilityProcessorException {
        File imageOutputFile = new File(moduleOutputPath + File.separator + "imageinfo.txt"); try (BufferedReader br = new BufferedReader(new FileReader(imageOutputFile))) {
            String fileRead = br.readLine();
            if (fileRead != null) {
                String[] profileLine = fileRead.split(":");  String[] memProfile = profileLine[1].split(",|\\("); return memProfile[0].replaceAll("\\s+", ""); } else {
                throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToParseImageInfo());
            }
        } catch (IOException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToParseImageInfo(), ex);
        }
    }

    @NbBundle.Messages({
        "# {0} - plugin name",
        "VolatilityProcessor_artifactAttribute_interestingFileSet=Volatility Plugin {0}",
        "# {0} - file path",
        "# {1} - file name",
        "# {2} - plugin name",
        "VolatilityProcessor_exceptionMessage_fileNotFound=File {0}/{1} not found for ouput of {2} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorCreatingArtifact=Error creating artifact for output of {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_errorFindingFiles=Error finding files parsed from output of {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_failedToIndexArtifact=Error indexing artifact from output of {0} plugin"
    })
    private void flagFiles(Set<String> fileSet, String pluginName) throws VolatilityProcessorException {
        for (String file : fileSet) {
            if (isCancelled) {
                return;
            }

            if (file.isEmpty()) {
                continue;
            }

            File volfile = new File(file);
            String fileName = volfile.getName().trim();
            if (fileName.length() < 1) {
                continue;
            }

            String filePath = volfile.getParent();

            logger.log(Level.INFO, "Looking up file {0} at path {1}", new Object[]{fileName, filePath});

            try {
                List<AbstractFile> resolvedFiles;
                if (filePath == null) {
                    resolvedFiles = fileManager.findFiles(fileName);
                } else {
                    filePath = filePath.replaceAll("\\\\", "/");  resolvedFiles = fileManager.findFiles(fileName, filePath);
                }

                if (resolvedFiles.isEmpty() && (fileName.contains(".") == false)) { if (fileSet.contains(file + ".exe")) { continue;
                    }

                    fileName += ".%"; logger.log(Level.INFO, "Looking up file (extension wildcard) {0} at path {1}", new Object[]{fileName, filePath});

                    resolvedFiles = filePath == null
                            ? fileManager.findFiles(fileName)
                            : fileManager.findFiles(fileName, filePath);
                }

                if (resolvedFiles.isEmpty()) {
                    errorMsgs.add(Bundle.VolatilityProcessor_exceptionMessage_fileNotFound(filePath, fileName, pluginName));
                    continue;
                }

                for (AbstractFile resolvedFile : resolvedFiles) {
                    if (resolvedFile.getType() == TSK_DB_FILES_TYPE_ENUM.SLACK) {
                        continue;
                    }
                    try {

                        String setName = Bundle.VolatilityProcessor_artifactAttribute_interestingFileSet(pluginName);
                        Collection<BlackboardAttribute> attributes = singleton(new BlackboardAttribute(TSK_SET_NAME, VOLATILITY, setName));

                        if (!blackboard.artifactExists(resolvedFile, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, attributes)) {
                            BlackboardArtifact volArtifact = resolvedFile.newAnalysisResult(
                                    BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT, Score.SCORE_LIKELY_NOTABLE, 
                                    null, setName, null, 
                                    attributes)
                                    .getAnalysisResult();

                            try {
                                blackboard.postArtifact(volArtifact, VOLATILITY);
                            } catch (Blackboard.BlackboardException ex) {
                                errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_failedToIndexArtifact(pluginName));
                                logger.log(Level.SEVERE, String.format("Failed to index artifact (artifactId=%d) for for output of %s plugin", volArtifact.getArtifactID(), pluginName), ex);
                            }
                        }
                    } catch (TskCoreException ex) {
                        throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_errorCreatingArtifact(pluginName), ex);
                    }
                }
            } catch (TskCoreException ex) {
                throw new VolatilityProcessorException(Bundle.VolatilityProcessor_errorMessage_errorFindingFiles(pluginName), ex);
            }
        }
    }

    private void createArtifactsFromPluginOutput(String pluginName, File pluginOutputFile) throws VolatilityProcessorException {
        progressMonitor.setProgressText("Parsing module " + pluginName);
        Set<String> fileSet = null;
        switch (pluginName) {
            case "dlllist": fileSet = parseDllListOutput(pluginOutputFile);
                break;
            case "handles": fileSet = parseHandlesOutput(pluginOutputFile);
                break;
            case "cmdline": fileSet = parseCmdlineOutput(pluginOutputFile);
                break;
            case "psxview": fileSet = parsePsxviewOutput(pluginOutputFile);
                break;
            case "pslist": fileSet = parsePslistOutput(pluginOutputFile);
                break;
            case "psscan": fileSet = parsePsscanOutput(pluginOutputFile);
                break;
            case "pstree": fileSet = parsePstreeOutput(pluginOutputFile);
                break;
            case "svcscan": fileSet = parseSvcscanOutput(pluginOutputFile);
                break;
            case "shimcache": fileSet = parseShimcacheOutput(pluginOutputFile);
                break;
            default:
                break;
        }

        if (fileSet != null && !fileSet.isEmpty()) {
            progressMonitor.setProgressText("Flagging files from module " + pluginName);
            flagFiles(fileSet, pluginName);
        }
    }

    private String normalizePath(String filePath) {
        if (filePath == null) {
            return ""; }
        String path = filePath.trim();

        path = path.replaceAll("\\\\", "/"); path = path.toLowerCase();

        if ((path.length() > 4) && (path.startsWith("/??/"))) { path = path.substring(4);
        }

        if (path.contains(":")) { int index = path.indexOf(":");
            if (index + 1 < path.length()) {
                path = path.substring(index + 1);
            }
        }

        path = path.replaceAll("/systemroot/", "/windows/");

        path = path.replaceAll("%systemroot%", "/windows/"); path = path.replaceAll("/device/", ""); if (path.contains("/harddiskvolume")) { int index = path.indexOf("/harddiskvolume"); if (index + 16 < path.length()) {
                path = path.substring(index + 16);
            }
        }

        if (path.startsWith("/namedpipe/")) { return ""; }

        return path;
    }

    @NbBundle.Messages({
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_outputParsingError=Error parsing output for {0} plugin"
    })
    private Set<String> parseHandlesOutput(File pluginOutputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(pluginOutputFile))) {
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") == false) { continue;
                }

                String TAG = " File "; String file_path;
                if ((line.contains(TAG)) && (line.length() > 57)) {
                    file_path = line.substring(57);
                    if (file_path.contains("\"")) { file_path = file_path.substring(0, file_path.indexOf('\"')); }
                    if (file_path.startsWith("\\Device\\")) { if (file_path.contains("HardDiskVolume") == false) { continue;
                        }
                    }

                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("handles"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("handles"), ex);
        }
        return fileSet;
    }

    private Set<String> parseDllListOutput(File outputFile) {
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") && line.length() > 33) {
                    String file_path = line.substring(33);
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("dlllist"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("dlllist"), ex);
        }
        return fileSet;
    }

    private Set<String> parseCmdlineOutput(File outputFile) {
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() > 16) {
                    String TAG = "Command line : "; if ((line.startsWith(TAG)) && line.length() > TAG.length() + 1) {
                        String file_path;

                        if (line.charAt(TAG.length()) == '\"') { file_path = line.substring(TAG.length() + 1);
                            if (file_path.contains("\"")) { file_path = file_path.substring(0, file_path.indexOf('\"')); }
                        } else {
                            file_path = line.substring(TAG.length());
                            if (file_path.contains(" ")) { file_path = file_path.substring(0, file_path.indexOf(' '));
                            }
                        }
                        fileSet.add(normalizePath(file_path));
                    }
                }
            }

        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("cmdline"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("cmdline"), ex);
        }
        return fileSet;
    }

    private Set<String> parseShimcacheOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                String file_path;
                if (line.length() > 62) {
                    file_path = line.substring(62);
                    if (file_path.contains("\"")) { file_path = file_path.substring(0, file_path.indexOf('\"')); }
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("shimcache"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("shimcache"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePsscanOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") == false) { continue;
                } else if (line.length() < 37) {
                    continue;
                }

                String file_path = line.substring(19, 37);
                file_path = normalizePath(file_path);

                if (file_path.equals("system")) { continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("psscan"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("psscan"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePslistOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") == false) { continue;
                }

                if (line.length() < 34) {
                    continue;
                }
                String file_path = line.substring(10, 34);
                file_path = normalizePath(file_path);

                if (file_path.equals("system")) { continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("pslist"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("pslist"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePsxviewOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") == false) { continue;
                }

                if (line.length() < 34) {
                    continue;
                }

                String file_path = line.substring(11, 34);
                file_path = normalizePath(file_path);

                if (file_path.equals("system")) { continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("psxview"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("psxview"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePstreeOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            while ((line = br.readLine()) != null) {
                String TAG = ":";
                if (line.contains(TAG)) {
                    int index = line.indexOf(TAG);
                    if (line.length() < 52 || index + 1 >= 52) {
                        continue;
                    }
                    String file_path = line.substring(line.indexOf(':') + 1, 52); file_path = normalizePath(file_path);

                    if (file_path.equals("system")) { continue;
                    }
                    fileSet.add(file_path);
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("pstree"));
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("pstree"), ex);
        }
        return fileSet;
    }

    private Set<String> parseSvcscanOutput(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(PluginFile));
            while ((line = br.readLine()) != null) {
                String file_path;
                String TAG = "Binary Path: ";
                if (line.startsWith(TAG) && line.length() > TAG.length() + 1) {
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length() + 1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf('\"'));
                        }
                    } else if (line.charAt(TAG.length()) == '-') {
                        continue;
                    } else {
                        file_path = line.substring(TAG.length());
                        if (file_path.contains(" ")) {
                            file_path = file_path.substring(0, file_path.indexOf(' '));
                        }
                        if (file_path.startsWith("\\Driver\\")) {
                            continue;
                        } else if (file_path.startsWith("\\FileSystem\\")) {
                            continue;
                        }
                    }
                    fileSet.add(normalizePath(file_path));
                }
            }
            br.close();
        } catch (IOException ex) {
            String msg = "Error parsing svcscan output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        }
        return fileSet;
    }

    void cancel() {
        isCancelled = true;
    }

    final class VolatilityProcessorException extends Exception {

        private static final long serialVersionUID = 1L;

        private VolatilityProcessorException(String message) {
            super(message);
        }

        private VolatilityProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
