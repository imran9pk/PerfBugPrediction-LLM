package com.google.devtools.build.lib.rules.android.databinding;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import java.util.Set;

public class DataBindingProcessorArgsBuilder {
  private final boolean useUpdatedArgs;
  private final ImmutableList.Builder<String> flags = ImmutableList.builder();

  public DataBindingProcessorArgsBuilder(boolean useUpdatedArgs) {
    this.useUpdatedArgs = useUpdatedArgs;
  }

  public DataBindingProcessorArgsBuilder metadataOutputDir(String metadataOutputDir) {
    if (useUpdatedArgs) {
      flags.add(
          createProcessorFlag(
              "dependencyArtifactsDir",
              metadataOutputDir + "/" + DataBinding.DEP_METADATA_INPUT_DIR));
      flags.add(
          createProcessorFlag(
              "aarOutDir", metadataOutputDir + "/" + DataBinding.METADATA_OUTPUT_DIR));
    } else {
      flags.add(createProcessorFlag("bindingBuildFolder", metadataOutputDir));
      flags.add(createProcessorFlag("generationalFileOutDir", metadataOutputDir));
    }

    return this;
  }

  public DataBindingProcessorArgsBuilder sdkDir(String sdkDir) {
    flags.add(createProcessorFlag("sdkDir", sdkDir));
    return this;
  }

  public DataBindingProcessorArgsBuilder binary(boolean isBinary) {
    flags.add(createProcessorFlag("artifactType", isBinary ? "APPLICATION" : "LIBRARY"));
    return this;
  }

  public DataBindingProcessorArgsBuilder exportClassListTo(String path) {
    if (useUpdatedArgs) {
      flags.add(createProcessorFlag("exportClassListOutFile", path));
    } else {
      flags.add(createProcessorFlag("exportClassListTo", path));
    }
    return this;
  }

  public DataBindingProcessorArgsBuilder modulePackage(String pkg) {
    flags.add(createProcessorFlag("modulePackage", pkg));
    return this;
  }

  public DataBindingProcessorArgsBuilder minApi(String minApi) {
    flags.add(createProcessorFlag("minApi", minApi));
    return this;
  }

  public DataBindingProcessorArgsBuilder printEncodedErrors() {
    flags.add(createProcessorFlag("printEncodedErrors", "1"));
    return this;
  }

  public DataBindingProcessorArgsBuilder enableV2() {
    flags.add(createProcessorFlag("enableV2", "1"));
    return this;
  }

  public DataBindingProcessorArgsBuilder classLogDir(String classLogDir) {
    flags.add(createProcessorFlag("classLogDir", classLogDir));
    return this;
  }

  public DataBindingProcessorArgsBuilder classLogDir(Artifact classLogDir) {
    flags.add(createProcessorFlag("classLogDir", classLogDir));
    return this;
  }

  public DataBindingProcessorArgsBuilder layoutInfoDir(String layoutInfoDir) {
    if (useUpdatedArgs) {
      flags.add(createProcessorFlag("layoutInfoDir", layoutInfoDir));
    } else {
      flags.add(createProcessorFlag("xmlOutDir", layoutInfoDir));
    }
    return this;
  }

  public DataBindingProcessorArgsBuilder layoutInfoDir(Artifact layoutInfoDir) {
    flags.add(createProcessorFlag("layoutInfoDir", layoutInfoDir));
    return this;
  }

  public DataBindingProcessorArgsBuilder directDependencyPkgs(Set<String> packages) {
    String value = String.format("[%s]", Joiner.on(",").join(packages));
    flags.add(createProcessorFlag("directDependencyPkgs", value));
    return this;
  }

  public ImmutableList<String> build() {
    return flags.build();
  }

  private static String createProcessorFlag(String flag, String value) {
    return String.format("-Aandroid.databinding.%s=%s", flag, value);
  }

  private static String createProcessorFlag(String flag, Artifact value) {
    return createProcessorFlag(flag, value.getExecPathString());
  }
}
