package com.google.devtools.build.lib.rules.android;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.rules.android.databinding.DataBindingContext;
import com.google.devtools.build.lib.vfs.PathFragment;
import javax.annotation.Nullable;

public class AndroidResourceParsingActionBuilder {

  @Nullable private Artifact manifest;
  @Nullable private String javaPackage;

  private AndroidResources resources = AndroidResources.empty();
  private AndroidAssets assets = AndroidAssets.empty();

  @Nullable private Artifact output;

  @Nullable private Artifact compiledSymbols;
  @Nullable private Artifact dataBindingInfoZip;

  public AndroidResourceParsingActionBuilder setOutput(Artifact output) {
    this.output = output;
    return this;
  }

  public AndroidResourceParsingActionBuilder setManifest(@Nullable Artifact manifest) {
    this.manifest = manifest;
    return this;
  }

  public AndroidResourceParsingActionBuilder setJavaPackage(@Nullable String javaPackage) {
    this.javaPackage = javaPackage;
    return this;
  }

  public AndroidResourceParsingActionBuilder setResources(AndroidResources resources) {
    this.resources = resources;
    return this;
  }

  public AndroidResourceParsingActionBuilder setAssets(AndroidAssets assets) {
    this.assets = assets;
    return this;
  }

  public AndroidResourceParsingActionBuilder setCompiledSymbolsOutput(
      @Nullable Artifact compiledSymbols) {
    this.compiledSymbols = compiledSymbols;
    return this;
  }

  public AndroidResourceParsingActionBuilder setDataBindingInfoZip(Artifact dataBindingInfoZip) {
    this.dataBindingInfoZip = dataBindingInfoZip;
    return this;
  }

  private static String convertRoots(Iterable<PathFragment> roots) {
    return Streams.stream(roots).map(Object::toString).collect(joining("#"));
  }

  private void build(AndroidDataContext dataContext) {
    String resourceDirectories =
        convertRoots(resources.getResourceRoots()) + ":" + convertRoots(assets.getAssetRoots());
    Iterable<Artifact> resourceArtifacts =
        Iterables.concat(assets.getAssets(), resources.getResources());

    if (output != null) {
      BusyBoxActionBuilder.create(dataContext, "PARSE")
          .addInput("--primaryData", resourceDirectories, resourceArtifacts)
          .addOutput("--output", output)
          .buildAndRegister("Parsing Android resources", "AndroidResourceParser");
    }

    if (compiledSymbols != null) {
      BusyBoxActionBuilder compiledBuilder =
          BusyBoxActionBuilder.create(dataContext, "COMPILE_LIBRARY_RESOURCES")
              .addAapt()
              .addInput("--resources", resourceDirectories, resourceArtifacts)
              .addOutput("--output", compiledSymbols)
              .maybeAddFlag("--useDataBindingAndroidX", dataContext.useDataBindingAndroidX());

      if (dataBindingInfoZip != null) {
        compiledBuilder
            .addInput("--manifest", manifest)
            .maybeAddFlag("--packagePath", javaPackage)
            .addOutput("--dataBindingInfoOut", dataBindingInfoZip);
      }

      compiledBuilder.buildAndRegister("Compiling Android resources", "AndroidResourceCompiler");
    }
  }

  public ParsedAndroidResources build(
      AndroidDataContext dataContext,
      AndroidResources androidResources,
      StampedAndroidManifest manifest,
      DataBindingContext dataBindingContext) {
    if (dataBindingInfoZip != null) {
      setManifest(manifest.getManifest());
      setJavaPackage(manifest.getPackage());
    }

    setResources(androidResources);
    build(dataContext);

    return ParsedAndroidResources.of(
        androidResources,
        output,
        compiledSymbols,
        dataContext.getLabel(),
        manifest,
        dataBindingContext);
  }

  public ParsedAndroidAssets build(AndroidDataContext dataContext, AndroidAssets assets) {
    setAssets(assets);
    build(dataContext);

    return ParsedAndroidAssets.of(assets, output, compiledSymbols, dataContext.getLabel());
  }
}
