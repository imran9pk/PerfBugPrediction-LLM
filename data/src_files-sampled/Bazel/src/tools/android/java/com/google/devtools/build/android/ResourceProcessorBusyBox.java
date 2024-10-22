package com.google.devtools.build.android;

import com.google.devtools.build.android.aapt2.Aapt2Exception;
import com.google.devtools.build.android.resources.JavaIdentifierValidator.InvalidJavaIdentifier;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse;
import com.google.devtools.common.options.EnumConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import com.google.devtools.common.options.OptionsParsingException;
import com.google.devtools.common.options.ShellQuotedParamsFilePreProcessor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceProcessorBusyBox {
  static enum Tool {
    GENERATE_BINARY_R() {
      @Override
      void call(String[] args) throws Exception {
        RClassGeneratorAction.main(args);
      }
    },
    PARSE() {
      @Override
      void call(String[] args) throws Exception {
        AndroidResourceParsingAction.main(args);
      }
    },
    MERGE_COMPILED() {
      @Override
      void call(String[] args) throws Exception {
        AndroidCompiledResourceMergingAction.main(args);
      }
    },
    GENERATE_AAR() {
      @Override
      void call(String[] args) throws Exception {
        AarGeneratorAction.main(args);
      }
    },
    MERGE_MANIFEST() {
      @Override
      void call(String[] args) throws Exception {
        ManifestMergerAction.main(args);
      }
    },
    COMPILE_LIBRARY_RESOURCES() {
      @Override
      void call(String[] args) throws Exception {
        CompileLibraryResourcesAction.main(args);
      }
    },
    LINK_STATIC_LIBRARY() {
      @Override
      void call(String[] args) throws Exception {
        ValidateAndLinkResourcesAction.main(args);
      }
    },
    AAPT2_PACKAGE() {
      @Override
      void call(String[] args) throws Exception {
        Aapt2ResourcePackagingAction.main(args);
      }
    },
    SHRINK_AAPT2() {
      @Override
      void call(String[] args) throws Exception {
        Aapt2ResourceShrinkingAction.main(args);
      }
    },
    AAPT2_OPTIMIZE() {
      @Override
      void call(String[] args) throws Exception {
        Aapt2OptimizeAction.main(args);
      }
    },
    MERGE_ASSETS() {
      @Override
      void call(String[] args) throws Exception {
        AndroidAssetMergingAction.main(args);
      }
    },
    PROCESS_DATABINDING {
      @Override
      void call(String[] args) throws Exception {
        AndroidDataBindingProcessingAction.main(args);
      }
    };

    abstract void call(String[] args) throws Exception;
  }

  private static final Logger logger = Logger.getLogger(ResourceProcessorBusyBox.class.getName());
  private static final Properties properties = loadSiteCustomizations();

  public static final class ToolConverter extends EnumConverter<Tool> {

    public ToolConverter() {
      super(Tool.class, "resource tool");
    }
  }

  public static final class Options extends OptionsBase {
    @Option(
        name = "tool",
        defaultValue = "null",
        converter = ToolConverter.class,
        category = "input",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help =
            "The processing tool to execute. "
                + "Valid tools: GENERATE_BINARY_R, PARSE, "
                + "GENERATE_AAR, MERGE_MANIFEST, COMPILE_LIBRARY_RESOURCES, "
                + "LINK_STATIC_LIBRARY, AAPT2_PACKAGE, SHRINK_AAPT2, MERGE_COMPILED.")
    public Tool tool;
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--persistent_worker")) {
      System.exit(runPersistentWorker());
    } else {
      System.exit(processRequest(Arrays.asList(args)));
    }
  }

  private static int runPersistentWorker() throws Exception {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(buf, true);
    PrintStream realStdOut = System.out;
    PrintStream realStdErr = System.err;
    try {
      System.setOut(ps);
      System.setErr(ps);

      while (true) {
        try {
          WorkRequest request = WorkRequest.parseDelimitedFrom(System.in);
          if (request == null) {
            break;
          }

          int exitCode = processRequest(request.getArgumentsList());
          ps.flush();

          WorkResponse.newBuilder()
              .setExitCode(exitCode)
              .setRequestId(request.getRequestId())
              .setOutput(buf.toString())
              .build()
              .writeDelimitedTo(realStdOut);

          realStdOut.flush();
          buf.reset();
        } catch (IOException e) {
          logger.severe(e.getMessage());
          e.printStackTrace(realStdErr);
          return 1;
        }
      }
    } finally {
      System.setOut(realStdOut);
      System.setErr(realStdErr);
    }
    return 0;
  }

  private static int processRequest(List<String> args) throws Exception {
    OptionsParser optionsParser =
        OptionsParser.builder()
            .optionsClasses(Options.class)
            .allowResidue(true)
            .argsPreProcessor(new ShellQuotedParamsFilePreProcessor(FileSystems.getDefault()))
            .build();
    Options options;
    try {
      optionsParser.parse(args);
      options = optionsParser.getOptions(Options.class);
      options.tool.call(optionsParser.getResidue().toArray(new String[0]));
    } catch (UserException e) {
      logger.log(Level.SEVERE, e.getMessage());
      return 1;
    } catch (OptionsParsingException | IOException | Aapt2Exception | InvalidJavaIdentifier e) {
      logSuppressed(e);
      throw e;
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error during processing", e);
      throw e;
    }
    return 0;
  }

  private static void logSuppressed(Throwable e) {
    Arrays.stream(e.getSuppressed()).map(Throwable::getMessage).forEach(logger::severe);
  }

  public static boolean getProperty(String name) {
    return Boolean.parseBoolean(properties.getProperty(name, "false"));
  }

  private static Properties loadSiteCustomizations() {
    Properties properties = new Properties();
    try (InputStream propertiesInput =
        ResourceProcessorBusyBox.class.getResourceAsStream("rpbb.properties")) {
      if (propertiesInput != null) {
        properties.load(propertiesInput);
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error loading site customizations", e);
    }
    return properties;
  }
}
