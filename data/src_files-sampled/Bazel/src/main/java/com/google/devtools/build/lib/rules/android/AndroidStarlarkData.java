package com.google.devtools.build.lib.rules.android;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifact;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.analysis.starlark.StarlarkErrorReporter;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.BazelStarlarkContext;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.packages.Provider;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.android.AndroidLibraryAarInfo.Aar;
import com.google.devtools.build.lib.rules.android.databinding.DataBinding;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgsProvider;
import com.google.devtools.build.lib.rules.java.JavaCompilationInfoProvider;
import com.google.devtools.build.lib.rules.java.JavaInfo;
import com.google.devtools.build.lib.rules.java.JavaRuleOutputJarsProvider;
import com.google.devtools.build.lib.rules.java.JavaSourceJarsProvider;
import com.google.devtools.build.lib.rules.java.ProguardSpecProvider;
import com.google.devtools.build.lib.starlarkbuildapi.android.AndroidBinaryDataSettingsApi;
import com.google.devtools.build.lib.starlarkbuildapi.android.AndroidDataProcessingApi;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;

public abstract class AndroidStarlarkData
    implements AndroidDataProcessingApi<
        AndroidDataContext,
        ConfiguredTarget,
        Artifact,
        SpecialArtifact,
        AndroidAssetsInfo,
        AndroidResourcesInfo,
        AndroidManifestInfo,
        AndroidLibraryAarInfo,
        AndroidBinaryDataInfo,
        ValidatedAndroidResources> {

  public abstract AndroidSemantics getAndroidSemantics();

  @Override
  public AndroidAssetsInfo assetsFromDeps(
      Sequence<?> deps, boolean neverlink,
      StarlarkThread thread)
      throws EvalException {
    Label label = BazelStarlarkContext.from(thread).getAnalysisRuleLabel();
    return AssetDependencies.fromProviders(
            Sequence.cast(deps, AndroidAssetsInfo.class, "deps"), neverlink)
        .toInfo(label);
  }

  @Override
  public AndroidResourcesInfo resourcesFromDeps(
      AndroidDataContext ctx,
      Sequence<?> deps, Sequence<?> assets, boolean neverlink,
      String customPackage)
      throws InterruptedException, EvalException {
    try (StarlarkErrorReporter errorReporter =
        StarlarkErrorReporter.from(ctx.getRuleErrorConsumer())) {
      return ResourceApk.processFromTransitiveLibraryData(
              ctx,
              DataBinding.getDisabledDataBindingContext(ctx),
              ResourceDependencies.fromProviders(
                  Sequence.cast(deps, AndroidResourcesInfo.class, "deps"),
                  neverlink),
              AssetDependencies.fromProviders(
                  Sequence.cast(assets, AndroidAssetsInfo.class, "assets"),
                  neverlink),
              StampedAndroidManifest.createEmpty(
                  ctx.getActionConstructionContext(), customPackage, false))
          .toResourceInfo(ctx.getLabel());
    }
  }

  @Override
  public AndroidManifestInfo stampAndroidManifest(
      AndroidDataContext ctx, Object manifest, Object customPackage, boolean exported)
      throws InterruptedException, EvalException {
    String pkg = fromNoneable(customPackage, String.class);
    try (StarlarkErrorReporter errorReporter =
        StarlarkErrorReporter.from(ctx.getRuleErrorConsumer())) {
      return AndroidManifest.from(
              ctx,
              errorReporter,
              fromNoneable(manifest, Artifact.class),
              getAndroidSemantics(),
              pkg,
              exported)
          .stamp(ctx)
          .toProvider();
    }
  }

  @Override
  public AndroidAssetsInfo mergeAssets(
      AndroidDataContext ctx,
      Object assets,
      Object assetsDir,
      Sequence<?> deps, boolean neverlink)
      throws EvalException, InterruptedException {
    StarlarkErrorReporter errorReporter = StarlarkErrorReporter.from(ctx.getRuleErrorConsumer());
    try {
      return AndroidAssets.from(
              errorReporter,
              isNone(assets) ? null : Sequence.cast(assets, ConfiguredTarget.class, "assets"),
              isNone(assetsDir) ? null : PathFragment.create((String) assetsDir))
          .process(
              ctx,
              AssetDependencies.fromProviders(
                  Sequence.cast(deps, AndroidAssetsInfo.class, "deps"), neverlink))
          .toProvider();
    } catch (RuleErrorException e) {
      throw handleRuleException(errorReporter, e);
    }
  }

  @Override
  public ValidatedAndroidResources mergeRes(
      AndroidDataContext ctx,
      AndroidManifestInfo manifest,
      Sequence<?> resources, Sequence<?> deps, boolean neverlink,
      boolean enableDataBinding)
      throws EvalException, InterruptedException {
    StarlarkErrorReporter errorReporter = StarlarkErrorReporter.from(ctx.getRuleErrorConsumer());
    try {
      return AndroidResources.from(
              errorReporter,
              getFileProviders(Sequence.cast(resources, ConfiguredTarget.class, "resources")),
              "resources")
          .process(
              ctx,
              manifest.asStampedManifest(),
              ResourceDependencies.fromProviders(
                  Sequence.cast(deps, AndroidResourcesInfo.class, "deps"), neverlink),
              DataBinding.contextFrom(
                  enableDataBinding, ctx.getActionConstructionContext(), ctx.getAndroidConfig()));
    } catch (RuleErrorException e) {
      throw handleRuleException(errorReporter, e);
    }
  }

  @Override
  public Dict<Provider, NativeInfo> mergeResources(
      AndroidDataContext ctx,
      AndroidManifestInfo manifest,
      Sequence<?> resources, Sequence<?> deps, boolean neverlink,
      boolean enableDataBinding)
      throws EvalException, InterruptedException {
    ValidatedAndroidResources validated =
        mergeRes(ctx, manifest, resources, deps, neverlink, enableDataBinding);
    JavaInfo javaInfo =
        getJavaInfoForRClassJar(validated.getClassJar(), validated.getJavaSourceJar());
    return Dict.<Provider, NativeInfo>builder()
        .put(AndroidResourcesInfo.PROVIDER, validated.toProvider())
        .put(JavaInfo.PROVIDER, javaInfo)
        .buildImmutable();
  }

  @Override
  public AndroidLibraryAarInfo makeAar(
      AndroidDataContext ctx,
      AndroidResourcesInfo resourcesInfo,
      AndroidAssetsInfo assetsInfo,
      Artifact libraryClassJar,
      Sequence<?> localProguardSpecs, Sequence<?> deps, boolean neverlink)
      throws EvalException, InterruptedException {
    if (neverlink) {
      return AndroidLibraryAarInfo.create(
          null,
          NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
          NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER));
    }

    Optional<? extends AndroidResources> resources =
        resourcesInfo.getDirectAndroidResources().toList().stream()
            .filter(r -> r.getLabel().equals(ctx.getLabel()))
            .findFirst();
    boolean definesLocalResources = resources.isPresent();

    Optional<? extends AndroidAssets> assets =
        assetsInfo.getDirectParsedAssets().toList().stream()
            .filter(a -> a.getLabel().equals(ctx.getLabel()))
            .findFirst();
    boolean definesLocalAssets = assets.isPresent() || assetsInfo.getValidationResult() != null;

    if (definesLocalResources != definesLocalAssets) {
      throw new EvalException(
          "Must define either both or none of assets and resources. Use the merge_assets and"
              + " merge_resources methods to define them, or assets_from_deps and"
              + " resources_from_deps to inherit without defining them.");
    }

    return Aar.makeAar(
            ctx,
            resources.isPresent() ? resources.get() : AndroidResources.empty(),
            assets.isPresent() ? assets.get() : AndroidAssets.empty(),
            resourcesInfo.getManifest(),
            resourcesInfo.getRTxt(),
            libraryClassJar,
            ImmutableList.copyOf(
                Sequence.cast(localProguardSpecs, Artifact.class, "local_proguard_specs")))
        .toProvider(
            Sequence.cast(deps, AndroidLibraryAarInfo.class, "deps"), definesLocalResources);
  }

  @Override
  public Dict<Provider, NativeInfo> processAarImportData(
      AndroidDataContext ctx,
      SpecialArtifact resources,
      SpecialArtifact assets,
      Artifact androidManifestArtifact,
      Sequence<?> deps) throws InterruptedException, EvalException {
    List<ConfiguredTarget> depsTargets = Sequence.cast(deps, ConfiguredTarget.class, "deps");

    ValidatedAndroidResources validatedResources =
        AndroidResources.forAarImport(resources)
            .process(
                ctx,
                AndroidManifest.forAarImport(androidManifestArtifact),
                ResourceDependencies.fromProviders(
                    getProviders(depsTargets, AndroidResourcesInfo.PROVIDER),
                    false),
                DataBinding.getDisabledDataBindingContext(ctx));

    MergedAndroidAssets mergedAssets =
        AndroidAssets.forAarImport(assets)
            .process(
                ctx,
                AssetDependencies.fromProviders(
                    getProviders(depsTargets, AndroidAssetsInfo.PROVIDER),
                    false));

    ResourceApk resourceApk = ResourceApk.of(validatedResources, mergedAssets, null, null);

    return getNativeInfosFrom(resourceApk, ctx.getLabel());
  }

  @Override
  public Dict<Provider, NativeInfo> processLocalTestData(
      AndroidDataContext ctx,
      Object manifest,
      Sequence<?> resources, Object assets,
      Object assetsDir,
      Object customPackage,
      String aaptVersionString,
      Dict<?, ?> manifestValues, Sequence<?> deps, Sequence<?> noCompressExtensions, Sequence<?> resourceConfigurationFilters, Sequence<?> densities) throws InterruptedException, EvalException {
    StarlarkErrorReporter errorReporter = StarlarkErrorReporter.from(ctx.getRuleErrorConsumer());
    List<ConfiguredTarget> depsTargets = Sequence.cast(deps, ConfiguredTarget.class, "deps");

    try {
      AndroidManifest rawManifest =
          AndroidManifest.from(
              ctx,
              errorReporter,
              fromNoneable(manifest, Artifact.class),
              fromNoneable(customPackage, String.class),
              false);

      ResourceApk resourceApk =
          AndroidLocalTestBase.buildResourceApk(
              ctx,
              getAndroidSemantics(),
              errorReporter,
              DataBinding.getDisabledDataBindingContext(ctx),
              rawManifest,
              AndroidResources.from(
                  errorReporter,
                  getFileProviders(
                      Sequence.cast(resources, ConfiguredTarget.class, "resource_files")),
                  "resource_files"),
              AndroidAssets.from(
                  errorReporter,
                  isNone(assets) ? null : Sequence.cast(assets, ConfiguredTarget.class, "assets"),
                  isNone(assetsDir)
                      ? null
                      : PathFragment.create(fromNoneable(assetsDir, String.class))),
              ResourceDependencies.fromProviders(
                  getProviders(depsTargets, AndroidResourcesInfo.PROVIDER),
                  false),
              AssetDependencies.fromProviders(
                  getProviders(depsTargets, AndroidAssetsInfo.PROVIDER), false),
              Dict.cast(manifestValues, String.class, String.class, "manifest_values"),
              Sequence.cast(noCompressExtensions, String.class, "nocompress_extensions"),
              ResourceFilterFactory.from(
                  Sequence.cast(
                      resourceConfigurationFilters, String.class, "resource_configuration_filters"),
                  Sequence.cast(densities, String.class, "densities")));

      Dict.Builder<Provider, NativeInfo> builder = Dict.builder();
      builder.putAll(getNativeInfosFrom(resourceApk, ctx.getLabel()));
      builder.put(
          AndroidBinaryDataInfo.PROVIDER,
          AndroidBinaryDataInfo.of(
              resourceApk.getArtifact(),
              resourceApk.getResourceProguardConfig(),
              resourceApk.toResourceInfo(ctx.getLabel()),
              resourceApk.toAssetsInfo(ctx.getLabel()),
              resourceApk.toManifestInfo().get()));
      return builder.buildImmutable();
    } catch (RuleErrorException e) {
      throw handleRuleException(errorReporter, e);
    }
  }

  private static IllegalStateException handleRuleException(
      StarlarkErrorReporter errorReporter, RuleErrorException exception) throws EvalException {
    errorReporter.close();
    throw new IllegalStateException("Unhandled RuleErrorException", exception);
  }

  @Override
  public BinaryDataSettings makeBinarySettings(
      AndroidDataContext ctx,
      Object shrinkResources,
      Sequence<?> resourceConfigurationFilters, Sequence<?> densities, Sequence<?> noCompressExtensions) throws EvalException {
    return new BinaryDataSettings(
        fromNoneableOrDefault(
            shrinkResources, Boolean.class, ctx.getAndroidConfig().useAndroidResourceShrinking()),
        ResourceFilterFactory.from(
            Sequence.cast(
                resourceConfigurationFilters, String.class, "resource_configuration_filters"),
            Sequence.cast(densities, String.class, "densities")),
        ImmutableList.copyOf(
            Sequence.cast(noCompressExtensions, String.class, "nocompress_extensions")));
  }

  @Override
  public Artifact resourcesFromValidatedRes(ValidatedAndroidResources resources) {
    return resources.getMergedResources();
  }

  private BinaryDataSettings defaultBinaryDataSettings(AndroidDataContext ctx)
      throws EvalException {
    return makeBinarySettings(
        ctx, Starlark.NONE, StarlarkList.empty(), StarlarkList.empty(), StarlarkList.empty());
  }

  private static class BinaryDataSettings implements AndroidBinaryDataSettingsApi {
    private final boolean shrinkResources;
    private final ResourceFilterFactory resourceFilterFactory;
    private final ImmutableList<String> noCompressExtensions;

    private BinaryDataSettings(
        boolean shrinkResources,
        ResourceFilterFactory resourceFilterFactory,
        ImmutableList<String> noCompressExtensions) {
      this.shrinkResources = shrinkResources;
      this.resourceFilterFactory = resourceFilterFactory;
      this.noCompressExtensions = noCompressExtensions;
    }
  }

  @Override
  public AndroidBinaryDataInfo processBinaryData(
      AndroidDataContext ctx,
      Sequence<?> resources,
      Object assets,
      Object assetsDir,
      Object manifest,
      Object customPackage,
      Dict<?, ?> manifestValues, Sequence<?> deps, String manifestMerger,
      Object maybeSettings,
      boolean crunchPng,
      boolean dataBindingEnabled)
      throws InterruptedException, EvalException {
    StarlarkErrorReporter errorReporter = StarlarkErrorReporter.from(ctx.getRuleErrorConsumer());
    List<ConfiguredTarget> depsTargets = Sequence.cast(deps, ConfiguredTarget.class, "deps");
    Map<String, String> manifestValueMap =
        Dict.cast(manifestValues, String.class, String.class, "manifest_values");

    try {
      BinaryDataSettings settings =
          fromNoneableOrDefault(
              maybeSettings, BinaryDataSettings.class, defaultBinaryDataSettings(ctx));

      AndroidManifest rawManifest =
          AndroidManifest.from(
              ctx,
              errorReporter,
              fromNoneable(manifest, Artifact.class),
              getAndroidSemantics(),
              fromNoneable(customPackage, String.class),
              false);

      ResourceDependencies resourceDeps =
          ResourceDependencies.fromProviders(
              getProviders(depsTargets, AndroidResourcesInfo.PROVIDER), false);

      StampedAndroidManifest stampedManifest =
          rawManifest.mergeWithDeps(
              ctx,
              getAndroidSemantics(),
              errorReporter,
              resourceDeps,
              manifestValueMap,
              manifestMerger);

      ResourceApk resourceApk =
          ProcessedAndroidData.processBinaryDataFrom(
                  ctx,
                  errorReporter,
                  stampedManifest,
                  AndroidBinary.shouldShrinkResourceCycles(
                      ctx.getAndroidConfig(), errorReporter, settings.shrinkResources),
                  manifestValueMap,
                  AndroidResources.from(
                      errorReporter,
                      getFileProviders(
                          Sequence.cast(resources, ConfiguredTarget.class, "resource_files")),
                      "resource_files"),
                  AndroidAssets.from(
                      errorReporter,
                      isNone(assets)
                          ? null
                          : Sequence.cast(assets, ConfiguredTarget.class, "assets"),
                      isNone(assetsDir) ? null : PathFragment.create((String) assetsDir)),
                  resourceDeps,
                  AssetDependencies.fromProviders(
                      getProviders(depsTargets, AndroidAssetsInfo.PROVIDER),
                      false),
                  settings.resourceFilterFactory,
                  settings.noCompressExtensions,
                  crunchPng,
                  DataBinding.contextFrom(
                      dataBindingEnabled,
                      ctx.getActionConstructionContext(),
                      ctx.getAndroidConfig()))
              .generateRClass(ctx);

      return AndroidBinaryDataInfo.of(
          resourceApk.getArtifact(),
          resourceApk.getResourceProguardConfig(),
          resourceApk.toResourceInfo(ctx.getLabel()),
          resourceApk.toAssetsInfo(ctx.getLabel()),
          resourceApk.toManifestInfo().get());

    } catch (RuleErrorException e) {
      throw handleRuleException(errorReporter, e);
    }
  }

  @Override
  public AndroidBinaryDataInfo shrinkDataApk(
      AndroidDataContext ctx,
      AndroidBinaryDataInfo binaryDataInfo,
      Artifact proguardOutputJar,
      Artifact proguardMapping,
      Object maybeSettings,
      Sequence<?> deps, Sequence<?> localProguardSpecs, Sequence<?> extraProguardSpecs) throws EvalException, InterruptedException {
    BinaryDataSettings settings =
        fromNoneableOrDefault(
            maybeSettings, BinaryDataSettings.class, defaultBinaryDataSettings(ctx));
    List<ConfiguredTarget> depsTargets = Sequence.cast(deps, ConfiguredTarget.class, "deps");

    if (!settings.shrinkResources) {
      return binaryDataInfo;
    }

    ImmutableList<Artifact> proguardSpecs =
        AndroidBinary.getProguardSpecs(
            ctx,
            getAndroidSemantics(),
            binaryDataInfo.getResourceProguardConfig(),
            binaryDataInfo.getManifestInfo().getManifest(),
            filesFromConfiguredTargets(
                Sequence.cast(localProguardSpecs, ConfiguredTarget.class, "proguard_specs")),
            filesFromConfiguredTargets(
                Sequence.cast(extraProguardSpecs, ConfiguredTarget.class, "extra_proguard_specs")),
            getProviders(depsTargets, ProguardSpecProvider.PROVIDER));

    if (!binaryDataInfo.getResourcesInfo().getDirectAndroidResources().isSingleton()) {
      throw new EvalException(
          "Expected exactly 1 direct android resource container, but found: "
              + binaryDataInfo.getResourcesInfo().getDirectAndroidResources());
    }

    if (!proguardSpecs.isEmpty()) {
      Artifact shrunkApk =
          AndroidBinary.shrinkResources(
              ctx,
              binaryDataInfo.getResourcesInfo().getDirectAndroidResources().toList().get(0),
              proguardOutputJar,
              proguardMapping,
              settings.resourceFilterFactory,
              settings.noCompressExtensions);
      return binaryDataInfo.withShrunkApk(shrunkApk);
    }

    return binaryDataInfo;
  }

  public static Dict<Provider, NativeInfo> getNativeInfosFrom(
      ResourceApk resourceApk, Label label) {
    Dict.Builder<Provider, NativeInfo> builder = Dict.builder();

    builder
        .put(AndroidResourcesInfo.PROVIDER, resourceApk.toResourceInfo(label))
        .put(AndroidAssetsInfo.PROVIDER, resourceApk.toAssetsInfo(label));

    resourceApk.toManifestInfo().ifPresent(info -> builder.put(AndroidManifestInfo.PROVIDER, info));

    builder.put(
        JavaInfo.PROVIDER,
        getJavaInfoForRClassJar(
            resourceApk.getResourceJavaClassJar(), resourceApk.getResourceJavaSrcJar()));

    return builder.buildImmutable();
  }

  private static JavaInfo getJavaInfoForRClassJar(Artifact rClassJar, Artifact rClassSrcJar) {
    return JavaInfo.Builder.create()
        .setNeverlink(true)
        .addProvider(
            JavaSourceJarsProvider.class,
            JavaSourceJarsProvider.builder().addSourceJar(rClassSrcJar).build())
        .addProvider(
            JavaRuleOutputJarsProvider.class,
            JavaRuleOutputJarsProvider.builder()
                .addOutputJar(rClassJar, null, null, ImmutableList.of(rClassSrcJar))
                .build())
        .addProvider(
            JavaCompilationArgsProvider.class,
            JavaCompilationArgsProvider.builder()
                .addDirectCompileTimeJar(rClassJar, rClassJar)
                .build())
        .addProvider(
            JavaCompilationInfoProvider.class,
            new JavaCompilationInfoProvider.Builder()
                .setCompilationClasspath(NestedSetBuilder.create(Order.NAIVE_LINK_ORDER, rClassJar))
                .build())
        .build();
  }

  public static boolean isNone(Object object) {
    return object == Starlark.NONE;
  }

  @Nullable
  public static <T> T fromNoneable(Object object, Class<T> clazz) {
    if (isNone(object)) {
      return null;
    }

    return clazz.cast(object);
  }

  public static <T> T fromNoneableOrDefault(Object object, Class<T> clazz, T defaultValue) {
    T value = fromNoneable(object, clazz);
    if (value == null) {
      return defaultValue;
    }

    return value;
  }

  private static ImmutableList<Artifact> filesFromConfiguredTargets(
      List<ConfiguredTarget> targets) {
    ImmutableList.Builder<Artifact> builder = ImmutableList.builder();
    for (FileProvider provider : getFileProviders(targets)) {
      builder.addAll(provider.getFilesToBuild().toList());
    }

    return builder.build();
  }

  private static ImmutableList<FileProvider> getFileProviders(List<ConfiguredTarget> targets) {
    return getProviders(targets, FileProvider.class);
  }

  private static <T extends TransitiveInfoProvider> ImmutableList<T> getProviders(
      List<ConfiguredTarget> targets, Class<T> clazz) {
    return targets
        .stream()
        .map(target -> target.getProvider(clazz))
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }

  public static <T extends NativeInfo> Sequence<T> getProviders(
      List<ConfiguredTarget> targets, BuiltinProvider<T> provider) {
    return StarlarkList.immutableCopyOf(
        targets.stream()
            .map(target -> target.get(provider))
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList()));
  }
}
