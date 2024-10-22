package org.jkiss.dbeaver.tasks.nativetool;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;

import java.io.File;

public abstract class AbstractImportExportSettings<BASE_OBJECT extends DBSObject> extends AbstractNativeToolSettings<BASE_OBJECT> {
    private static final Log log = Log.getLog(AbstractImportExportSettings.class);

    private File outputFolder;
    private String outputFilePattern;

    public File getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    public String getOutputFilePattern() {
        return outputFilePattern;
    }

    public void setOutputFilePattern(String outputFilePattern) {
        this.outputFilePattern = outputFilePattern;
    }

    public void fillExportObjectsFromInput() {

    }

    @Override
    public void loadSettings(DBRRunnableContext runnableContext, DBPPreferenceStore store) throws DBException {
        super.loadSettings(runnableContext, store);
        this.outputFilePattern = store.getString("export.outputFilePattern");
        if (CommonUtils.isEmpty(this.outputFilePattern)) {
            this.outputFilePattern = "dump-${database}-${timestamp}.sql";
        }
        String outputFolderPath = CommonUtils.toString(store.getString("export.outputFolder"));
        if (CommonUtils.isNotEmpty(outputFolderPath)) {
            File outputFolder = new File(outputFolderPath);
            if (outputFolder.exists()) {
                this.outputFolder = outputFolder;
            } else {
                log.warn("Output directory does not exists, using user home directory instead");
            }
        }
        if (this.outputFolder == null) {
            this.outputFolder = new File(RuntimeUtils.getUserHomeDir().getAbsolutePath());
        }
    }

    @Override
    public void saveSettings(DBRRunnableContext runnableContext, DBPPreferenceStore preferenceStore) {
        super.saveSettings(runnableContext, preferenceStore);
        preferenceStore.setValue("export.outputFilePattern", this.outputFilePattern);
        preferenceStore.setValue("export.outputFolder", this.outputFolder.getAbsolutePath());
    }

}
