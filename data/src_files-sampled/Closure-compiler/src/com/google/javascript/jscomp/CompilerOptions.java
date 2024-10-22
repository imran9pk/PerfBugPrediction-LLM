package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Chars;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class CompilerOptions implements Serializable {
  static final int DEFAULT_LINE_LENGTH_THRESHOLD = 500;

  private static final char[] POLYMER_PROPERTY_RESERVED_FIRST_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ$".toCharArray();
  private static final char[] POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS = "_$".toCharArray();
  private static final char[] ANGULAR_PROPERTY_RESERVED_FIRST_CHARS = {'$'};

  public static ImmutableSet<Character> getAngularPropertyReservedFirstChars() {
    return ImmutableSet.copyOf(Chars.asList(ANGULAR_PROPERTY_RESERVED_FIRST_CHARS));
  }

  public boolean shouldRunCrossChunkCodeMotion() {
    return crossChunkCodeMotion;
  }

  public boolean shouldRunCrossChunkMethodMotion() {
    return crossChunkMethodMotion;
  }

  public enum Reach {
    ALL,
    LOCAL_ONLY,
    NONE;

    public boolean isOn() {
      return this != NONE;
    }

    public boolean includesGlobals() {
      return this == ALL;
    }
  }

  public enum PropertyCollapseLevel {
    ALL,
    NONE,
    MODULE_EXPORT
  }

  private Optional<Boolean> emitUseStrict = Optional.absent();

  private LanguageMode languageIn;

  private Optional<FeatureSet> outputFeatureSet = Optional.absent();

  private Optional<Boolean> languageOutIsDefaultStrict = Optional.absent();

  private boolean skipUnsupportedPasses = false;

  private Environment environment;

  private class BrowserFeaturesetYear implements Serializable {

    private Integer year = 0;

    public Integer getYear() {
      return this.year;
    }

    public void setYear(Integer inputYear) {
      this.year = inputYear;
      this.setDependentValuesFromYear();
    }

    public void setDependentValuesFromYear() {
      if (year != 0) {
        if (year == 2021) {
          CompilerOptions.this.setOutputFeatureSet(FeatureSet.BROWSER_2021);
        } else if (year == 2020) {
          CompilerOptions.this.setOutputFeatureSet(FeatureSet.BROWSER_2020);
        } else if (year == 2019) {
          CompilerOptions.this.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
        } else if (year == 2018) {
          CompilerOptions.this.setLanguageOut(LanguageMode.ECMASCRIPT_2016);
        } else if (year == 2012) {
          CompilerOptions.this.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
        }
      }
    }
  }

  private final BrowserFeaturesetYear browserFeaturesetYear;

  public Integer getBrowserFeaturesetYear() {
    return this.browserFeaturesetYear.getYear();
  }

  public void validateBrowserFeaturesetYearOption(Integer inputYear) {
    checkState(
        inputYear == 2021
            || inputYear == 2020
            || inputYear == 2019
            || inputYear == 2018
            || inputYear == 2012,
        SimpleFormat.format(
            "Illegal browser_featureset_year=%d. We support values 2012, 2018, 2019, 2020 and 2021"
                + " only",
            inputYear));
  }

  public void setBrowserFeaturesetYear(Integer year) {
    validateBrowserFeaturesetYearOption(year);
    this.browserFeaturesetYear.setYear(year);
    this.setDefineToNumberLiteral("goog.FEATURESET_YEAR", year);
  }

  private boolean instrumentForCoverageOnly = false;

  public void setInstrumentForCoverageOnly(boolean instrumentForCoverageOnly) {
    this.instrumentForCoverageOnly = instrumentForCoverageOnly;
  }

  public boolean getInstrumentForCoverageOnly() {
    return instrumentForCoverageOnly;
  }

  @Nullable private Path typedAstOutputFile = null;

  public void setTypedAstOutputFile(@Nullable Path file) {
    this.typedAstOutputFile = file;
  }

  @Nullable
  Path getTypedAstOutputFile() {
    return this.typedAstOutputFile;
  }

  @Deprecated
  public void setSkipTranspilationAndCrash(boolean value) {}

  public void setInputSourceMaps(final ImmutableMap<String, SourceMapInput> inputSourceMaps) {
    this.inputSourceMaps = inputSourceMaps;
  }

  boolean inferConsts = true;

  public void setInferConst(boolean value) {
    inferConsts = value;
  }

  private boolean assumeStrictThis;

  private boolean preserveDetailedSourceInfo = false;
  private boolean preserveNonJSDocComments = false;
  private boolean continueAfterErrors = false;

  private boolean checkMissingOverrideTypes = false;

  public void setCheckMissingOverrideTypes(boolean value) {
    this.checkMissingOverrideTypes = value;
  }

  public boolean isCheckingMissingOverrideTypes() {
    return this.checkMissingOverrideTypes;
  }

  public enum IncrementalCheckMode {
    OFF,

    GENERATE_IJS,

    RUN_IJS_CHECKS_LATE,
  }

  private IncrementalCheckMode incrementalCheckMode = IncrementalCheckMode.OFF;

  public void setIncrementalChecks(IncrementalCheckMode value) {
    incrementalCheckMode = value;
    switch (value) {
      case OFF:
      case RUN_IJS_CHECKS_LATE:
        break;
      case GENERATE_IJS:
        setPreserveTypeAnnotations(true);
        setOutputJs(OutputJs.NORMAL);
        break;
    }
  }

  public boolean shouldGenerateTypedExterns() {
    return incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
  }

  public boolean shouldRunTypeSummaryChecksLate() {
    return incrementalCheckMode == IncrementalCheckMode.RUN_IJS_CHECKS_LATE;
  }

  private Config.JsDocParsing parseJsDocDocumentation = Config.JsDocParsing.TYPES_ONLY;

  private boolean printExterns;

  void setPrintExterns(boolean printExterns) {
    this.printExterns = printExterns;
  }

  boolean shouldPrintExterns() {
    return this.printExterns || incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
  }

  boolean inferTypes;

  boolean skipNonTranspilationPasses;

  DevMode devMode;

  private boolean checkDeterminism;

  private DependencyOptions dependencyOptions = DependencyOptions.none();

  public MessageBundle messageBundle = null;

  private boolean strictMessageReplacement;

  public boolean checkSymbols;

  public boolean checkSuspiciousCode;

  public boolean checkTypes;

  @Deprecated
  public void setCheckGlobalThisLevel(CheckLevel level) {}

  Set<String> extraAnnotationNames;

  int numParallelThreads = 1;

  public void setNumParallelThreads(int parallelism) {
    numParallelThreads = parallelism;
  }

  public boolean foldConstants;

  public boolean deadAssignmentElimination;

  public boolean inlineConstantVars;

  int maxFunctionSizeAfterInlining;

  static final int UNLIMITED_FUN_SIZE_AFTER_INLINING = -1;

  boolean assumeClosuresOnlyCaptureReferences;

  private boolean inlineProperties;

  private boolean crossChunkCodeMotion;

  boolean crossChunkCodeMotionNoStubMethods;

  boolean parentChunkCanSeeSymbolsDeclaredInChildren;

  public boolean coalesceVariableNames;

  private boolean crossChunkMethodMotion;

  boolean inlineGetters;

  public boolean inlineVariables;

  boolean inlineLocalVariables;

  public boolean flowSensitiveInlineVariables;

  public boolean smartNameRemoval;

  public boolean removeDeadCode;

  public enum ExtractPrototypeMemberDeclarationsMode {
    OFF,
    USE_GLOBAL_TEMP,
    USE_CHUNK_TEMP,
    USE_IIFE
  }

  ExtractPrototypeMemberDeclarationsMode extractPrototypeMemberDeclarations;

  public boolean removeUnusedPrototypeProperties;

  public boolean removeUnusedClassProperties;

  boolean removeUnusedConstructorProperties;

  public boolean removeUnusedVars;

  public boolean removeUnusedLocalVars;

  public boolean collapseVariableDeclarations;

  public boolean collapseAnonymousFunctions;

  private AliasStringsMode aliasStringsMode;

  boolean outputJsStringUsage;

  public boolean convertToDottedProperties;

  public boolean rewriteFunctionExpressions;

  public boolean optimizeCalls;

  boolean optimizeESClassConstructors;

  public boolean optimizeArgumentsArray;

  boolean useTypesForLocalOptimization;

  boolean useSizeHeuristicToStopOptimizationLoop = true;

  int optimizationLoopMaxIterations;

  public VariableRenamingPolicy variableRenaming;

  PropertyRenamingPolicy propertyRenaming;

  public boolean labelRenaming;

  public boolean reserveRawExports;

  boolean preferStableNames;

  public boolean generatePseudoNames;

  public String renamePrefix;

  public String renamePrefixNamespace;

  boolean renamePrefixNamespaceAssumeCrossChunkNames = false;

  @VisibleForTesting
  public void setRenamePrefixNamespaceAssumeCrossChunkNames(boolean assume) {
    renamePrefixNamespaceAssumeCrossChunkNames = assume;
  }

  private PropertyCollapseLevel collapsePropertiesLevel;

  @Deprecated
  public boolean shouldCollapseProperties() {
    return collapsePropertiesLevel != PropertyCollapseLevel.NONE;
  }

  public PropertyCollapseLevel getPropertyCollapseLevel() {
    return collapsePropertiesLevel;
  }

  boolean collapseObjectLiterals;

  public void setCollapseObjectLiterals(boolean enabled) {
    collapseObjectLiterals = enabled;
  }

  public boolean getCollapseObjectLiterals() {
    return collapseObjectLiterals;
  }

  public boolean devirtualizeMethods;

  public boolean computeFunctionSideEffects;

  private boolean disambiguateProperties;

  private boolean ambiguateProperties;

  ImmutableMap<String, SourceMapInput> inputSourceMaps;

  VariableMap inputVariableMap;

  VariableMap inputPropertyMap;

  public boolean exportTestFunctions;

  NameGenerator nameGenerator;

  public void setNameGenerator(NameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  private boolean replaceMessagesWithChromeI18n;

  String tcProjectId;

  public void setReplaceMessagesWithChromeI18n(
      boolean replaceMessagesWithChromeI18n, String tcProjectId) {
    if (replaceMessagesWithChromeI18n
        && messageBundle != null
        && !(messageBundle instanceof EmptyMessageBundle)) {
      throw new RuntimeException(
          "When replacing messages with"
              + " chrome.i18n.getMessage, a message bundle should not be specified.");
    }

    this.replaceMessagesWithChromeI18n = replaceMessagesWithChromeI18n;
    this.tcProjectId = tcProjectId;
  }

  public boolean shouldRunReplaceMessagesForChrome() {
    if (replaceMessagesWithChromeI18n) {
      checkState(
          messageBundle == null || messageBundle instanceof EmptyMessageBundle,
          "When replacing messages with chrome.i18n.getMessage, a message bundle should not be"
              + " specified.");
      checkState(
          !doLateLocalization, "Late localization is not supported for chrome.i18n.getMessage");
      return true;
    } else {
      return false;
    }
  }

  boolean runtimeTypeCheck;

  String runtimeTypeCheckLogFunction;

  private CodingConvention codingConvention;

  @Nullable public String syntheticBlockStartMarker;
  @Nullable public String syntheticBlockEndMarker;

  public String locale;

  private boolean doLateLocalization;

  public boolean markAsCompiled;

  public boolean closurePass;

  private boolean preserveClosurePrimitives;

  boolean angularPass;

  @Nullable Integer polymerVersion;

  PolymerExportPolicy polymerExportPolicy;

  private boolean chromePass;

  J2clPassMode j2clPassMode;

  boolean j2clMinifierEnabled = true;

  @Nullable String j2clMinifierPruningManifest = null;

  boolean removeAbstractMethods;

  boolean removeClosureAsserts;

  boolean removeJ2clAsserts = true;

  public boolean gatherCssNames;

  ImmutableSet<String> stripTypes;

  ImmutableSet<String> stripNameSuffixes;

  ImmutableSet<String> stripNamePrefixes;

  protected transient Multimap<CustomPassExecutionTime, CompilerPass> customPasses;

  private final LinkedHashMap<String, Object> defineReplacements;

  private TweakProcessing tweakProcessing;

  public boolean rewriteGlobalDeclarationsForTryCatchWrapping;

  boolean checksOnly;

  public static enum OutputJs {
    NONE,
    SENTINEL,
    NORMAL,
  }

  OutputJs outputJs;

  public boolean generateExports;

  boolean exportLocalPropertyDefinitions;

  public CssRenamingMap cssRenamingMap;

  Set<String> cssRenamingSkiplist;

  boolean replaceIdGenerators = true; ImmutableMap<String, RenamingMap> idGenerators;

  Xid.HashFunction xidHashFunction;

  String idGeneratorsMapSerialized;

  List<String> replaceStringsFunctionDescriptions;

  String replaceStringsPlaceholderToken;
  VariableMap replaceStringsInputMap;

  private ImmutableSet<String> propertiesThatMustDisambiguate;

  boolean transformAMDToCJSModules = false;

  private boolean processCommonJSModules = false;

  List<String> moduleRoots = ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

  boolean rewritePolyfills = false;

  private boolean isolatePolyfills = false;

  List<String> forceLibraryInjection = ImmutableList.of();

  boolean preventLibraryInjection = false;

  boolean assumeForwardDeclaredForMissingTypes = false;

  @Nullable ImmutableSet<String> unusedImportsToRemove;

  public void setAssumeForwardDeclaredForMissingTypes(
      boolean assumeForwardDeclaredForMissingTypes) {
    this.assumeForwardDeclaredForMissingTypes = assumeForwardDeclaredForMissingTypes;
  }

  public boolean preserveTypeAnnotations;

  private boolean prettyPrint;

  public boolean lineBreak;

  public boolean preferLineBreakAtEndOfFile;

  public boolean printInputDelimiter;

  public String inputDelimiter = "// Input %num%";

  @Nullable private Path debugLogDirectory;

  private boolean quoteKeywordProperties;

  boolean preferSingleQuotes;

  public void setPreferSingleQuotes(boolean enabled) {
    this.preferSingleQuotes = enabled;
  }

  boolean trustedStrings;

  public void setTrustedStrings(boolean yes) {
    trustedStrings = yes;
  }

  boolean printSourceAfterEachPass;

  List<String> filesToPrintAfterEachPassRegexList = ImmutableList.of();
  List<String> chunksToPrintAfterEachPassRegexList = ImmutableList.of();
  List<String> qnameUsesToPrintAfterEachPassList = ImmutableList.of();

  public void setPrintSourceAfterEachPass(boolean printSource) {
    this.printSourceAfterEachPass = printSource;
  }

  public void setFilesToPrintAfterEachPassRegexList(List<String> filePathRegexList) {
    this.filesToPrintAfterEachPassRegexList = filePathRegexList;
  }

  public void setChunksToPrintAfterEachPassRegexList(List<String> chunkPathRegexList) {
    this.chunksToPrintAfterEachPassRegexList = chunkPathRegexList;
  }

  @Deprecated
  public void setModulesToPrintAfterEachPassRegexList(List<String> chunkPathRegexList) {
    this.chunksToPrintAfterEachPassRegexList = chunkPathRegexList;
  }

  public void setQnameUsesToPrintAfterEachPassList(List<String> qnameRegexList) {
    this.qnameUsesToPrintAfterEachPassList = qnameRegexList;
  }

  private TracerMode tracer;

  public TracerMode getTracerMode() {
    return tracer;
  }

  public void setTracerMode(TracerMode mode) {
    this.tracer = mode;
  }

  private Path tracerOutput;

  Path getTracerOutput() {
    return tracerOutput;
  }

  public void setTracerOutput(Path out) {
    tracerOutput = out;
  }

  private boolean colorizeErrorOutput;

  public ErrorFormat errorFormat;

  private ComposeWarningsGuard warningsGuard = new ComposeWarningsGuard();

  int summaryDetailLevel = 1;

  int lineLengthThreshold = DEFAULT_LINE_LENGTH_THRESHOLD;

  boolean useOriginalNamesInOutput = false;

  private boolean externExports;

  String externExportsPath;

  private final List<SortingErrorManager.ErrorReportGenerator> extraReportGenerators =
      new ArrayList<>();

  List<SortingErrorManager.ErrorReportGenerator> getExtraReportGenerators() {
    return extraReportGenerators;
  }

  void addReportGenerator(SortingErrorManager.ErrorReportGenerator generator) {
    extraReportGenerators.add(generator);
  }

  public String sourceMapOutputPath;

  public SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;

  public SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

  boolean parseInlineSourceMaps = true;

  boolean applyInputSourceMaps = false;

  boolean resolveSourceMapAnnotations = true;

  public List<? extends SourceMap.LocationMapping> sourceMapLocationMappings = ImmutableList.of();

  boolean sourceMapIncludeSourcesContent = false;

  transient Charset outputCharset;

  private boolean protectHiddenSideEffects;

  public void setProtectHiddenSideEffects(boolean enable) {
    this.protectHiddenSideEffects = enable;
  }

  public boolean shouldProtectHiddenSideEffects() {
    return protectHiddenSideEffects && !checksOnly;
  }

  private boolean assumeGettersArePure = true;

  public void setAssumeGettersArePure(boolean x) {
    this.assumeGettersArePure = x;
  }

  public boolean getAssumeGettersArePure() {
    return assumeGettersArePure;
  }

  private boolean assumeStaticInheritanceIsNotUsed = true;

  public void setAssumeStaticInheritanceIsNotUsed(boolean x) {
    this.assumeStaticInheritanceIsNotUsed = x;
  }

  public boolean getAssumeStaticInheritanceIsNotUsed() {
    return assumeStaticInheritanceIsNotUsed;
  }

  private transient AliasTransformationHandler aliasHandler;

  transient ErrorHandler errorHandler;

  private InstrumentOption instrumentForCoverageOption;

  private String productionInstrumentationArrayName;

  private static final ImmutableList<ConformanceConfig> GLOBAL_CONFORMANCE_CONFIGS =
      ImmutableList.of(ResourceLoader.loadGlobalConformance(CompilerOptions.class));

  private ImmutableList<ConformanceConfig> conformanceConfigs = GLOBAL_CONFORMANCE_CONFIGS;

  private Optional<Pattern> conformanceRemoveRegexFromPath =
      Optional.of(
          Pattern.compile("^((.*/)?google3/)?(/?(blaze|bazel)-out/[^/]+/(bin/|(?=genfiles/)))?"));

  public void setConformanceRemoveRegexFromPath(Optional<Pattern> pattern) {
    conformanceRemoveRegexFromPath = pattern;
  }

  public Optional<Pattern> getConformanceRemoveRegexFromPath() {
    return conformanceRemoveRegexFromPath;
  }

  boolean wrapGoogModulesForWhitespaceOnly = true;

  public void setWrapGoogModulesForWhitespaceOnly(boolean enable) {
    this.wrapGoogModulesForWhitespaceOnly = enable;
  }

  boolean printConfig = false;

  private Optional<Boolean> isStrictModeInput = Optional.absent();

  private boolean rewriteModulesBeforeTypechecking = false;
  private boolean enableModuleRewriting = true;

  public void setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(boolean b) {
    this.rewriteModulesBeforeTypechecking = b;
  }

  boolean shouldRewriteModulesBeforeTypechecking() {
    return this.enableModuleRewriting
        && (this.rewriteModulesBeforeTypechecking || this.processCommonJSModules);
  }

  public void setEnableModuleRewriting(boolean enable) {
    this.enableModuleRewriting = enable;
  }

  boolean shouldRewriteModulesAfterTypechecking() {
    return this.enableModuleRewriting && !this.rewriteModulesBeforeTypechecking;
  }

  boolean shouldRewriteModules() {
    return this.enableModuleRewriting;
  }

  private boolean rewriteProvidesInChecksOnly;

  public void setBadRewriteProvidesInChecksOnlyThatWeWantToGetRidOf(boolean b) {
    this.rewriteProvidesInChecksOnly = b;
  }

  boolean shouldRewriteProvidesInChecksOnly() {
    return this.rewriteProvidesInChecksOnly && this.shouldRewriteModules();
  }

  ResolutionMode moduleResolutionMode;

  private ImmutableMap<String, String> browserResolverPrefixReplacements;

  private ModuleLoader.PathEscaper pathEscaper;

  List<String> packageJsonEntryNames;

  public void setPrintConfig(boolean printConfig) {
    this.printConfig = printConfig;
  }

  private boolean allowDynamicImport = false;

  public void setAllowDynamicImport(boolean value) {
    this.allowDynamicImport = value;
  }

  boolean shouldAllowDynamicImport() {
    return this.allowDynamicImport;
  }

  private String dynamicImportAlias = null;

  public String getDynamicImportAlias() {
    return this.dynamicImportAlias;
  }

  public void setDynamicImportAlias(String value) {
    this.dynamicImportAlias = value;
  }

  boolean shouldAliasDynamicImport() {
    return this.dynamicImportAlias != null;
  }

  ChunkOutputType chunkOutputType;

  public CompilerOptions() {
    languageIn = LanguageMode.STABLE_IN;
    browserFeaturesetYear = new BrowserFeaturesetYear();

    environment = Environment.BROWSER;
    browserResolverPrefixReplacements = ImmutableMap.of();

    moduleResolutionMode = ModuleLoader.ResolutionMode.BROWSER;
    packageJsonEntryNames = ImmutableList.of("browser", "module", "main");
    pathEscaper = ModuleLoader.PathEscaper.ESCAPE;
    rewriteModulesBeforeTypechecking = false;
    rewriteProvidesInChecksOnly = false;
    enableModuleRewriting = true;

    skipNonTranspilationPasses = false;
    devMode = DevMode.OFF;
    checkDeterminism = false;
    checkSymbols = false;
    checkSuspiciousCode = false;
    checkTypes = false;
    computeFunctionSideEffects = false;
    extraAnnotationNames = null;

    foldConstants = false;
    coalesceVariableNames = false;
    deadAssignmentElimination = false;
    inlineConstantVars = false;
    inlineFunctionsLevel = Reach.NONE;
    maxFunctionSizeAfterInlining = UNLIMITED_FUN_SIZE_AFTER_INLINING;
    assumeStrictThis = false;
    assumeClosuresOnlyCaptureReferences = false;
    inlineProperties = false;
    crossChunkCodeMotion = false;
    parentChunkCanSeeSymbolsDeclaredInChildren = false;
    crossChunkMethodMotion = false;
    inlineGetters = false;
    inlineVariables = false;
    inlineLocalVariables = false;
    smartNameRemoval = false;
    removeDeadCode = false;
    extractPrototypeMemberDeclarations = ExtractPrototypeMemberDeclarationsMode.OFF;
    removeUnusedPrototypeProperties = false;
    removeUnusedClassProperties = false;
    removeUnusedVars = false;
    removeUnusedLocalVars = false;
    collapseVariableDeclarations = false;
    collapseAnonymousFunctions = false;
    aliasStringsMode = AliasStringsMode.NONE;
    outputJsStringUsage = false;
    convertToDottedProperties = false;
    rewriteFunctionExpressions = false;

    variableRenaming = VariableRenamingPolicy.OFF;
    propertyRenaming = PropertyRenamingPolicy.OFF;
    labelRenaming = false;
    generatePseudoNames = false;
    preferStableNames = false;
    renamePrefix = null;
    collapsePropertiesLevel = PropertyCollapseLevel.NONE;
    collapseObjectLiterals = false;
    devirtualizeMethods = false;
    disambiguateProperties = false;
    ambiguateProperties = false;
    exportTestFunctions = false;
    nameGenerator = new DefaultNameGenerator();

    runtimeTypeCheck = false;
    runtimeTypeCheckLogFunction = null;
    syntheticBlockStartMarker = null;
    syntheticBlockEndMarker = null;
    locale = null;
    doLateLocalization = false;
    markAsCompiled = false;
    closurePass = false;
    preserveClosurePrimitives = false;
    angularPass = false;
    polymerVersion = null;
    polymerExportPolicy = PolymerExportPolicy.LEGACY;
    j2clPassMode = J2clPassMode.AUTO;
    j2clMinifierEnabled = true;
    removeAbstractMethods = false;
    removeClosureAsserts = false;
    stripTypes = ImmutableSet.of();
    stripNameSuffixes = ImmutableSet.of();
    stripNamePrefixes = ImmutableSet.of();
    customPasses = null;
    defineReplacements = new LinkedHashMap<>();
    tweakProcessing = TweakProcessing.OFF;
    rewriteGlobalDeclarationsForTryCatchWrapping = false;
    checksOnly = false;
    outputJs = OutputJs.NORMAL;
    generateExports = true;
    exportLocalPropertyDefinitions = true;
    cssRenamingMap = null;
    cssRenamingSkiplist = null;
    idGenerators = ImmutableMap.of();
    replaceStringsFunctionDescriptions = ImmutableList.of();
    replaceStringsPlaceholderToken = "";
    propertiesThatMustDisambiguate = ImmutableSet.of();
    inputSourceMaps = ImmutableMap.of();

    instrumentForCoverageOption = InstrumentOption.NONE;
    productionInstrumentationArrayName = "";

    preserveTypeAnnotations = false;
    printInputDelimiter = false;
    prettyPrint = false;
    lineBreak = false;
    preferLineBreakAtEndOfFile = false;
    tracer = TracerMode.OFF;
    colorizeErrorOutput = false;
    errorFormat = ErrorFormat.FULL;
    externExports = false;
    chunkOutputType = ChunkOutputType.GLOBAL_NAMESPACE;

    aliasHandler = NULL_ALIAS_TRANSFORMATION_HANDLER;
    errorHandler = null;
    printSourceAfterEachPass = false;
    strictMessageReplacement = false;
  }

  public boolean isRemoveUnusedClassProperties() {
    return removeUnusedClassProperties;
  }

  public void setRemoveUnusedClassProperties(boolean removeUnusedClassProperties) {
    this.removeUnusedClassProperties = removeUnusedClassProperties;
  }

  public ImmutableMap<String, Node> getDefineReplacements() {
    ImmutableMap.Builder<String, Node> map = ImmutableMap.builder();
    for (Map.Entry<String, Object> entry : this.defineReplacements.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Boolean) {
        map.put(name, NodeUtil.booleanNode(((Boolean) value).booleanValue()));
      } else if (value instanceof Number) {
        map.put(name, NodeUtil.numberNode(((Number) value).doubleValue(), null));
      } else if (value instanceof String) {
        map.put(name, IR.string((String) value));
      } else {
        throw new IllegalStateException(String.valueOf(value));
      }
    }
    return map.buildOrThrow();
  }

  public void setDefineToBooleanLiteral(String defineName, boolean value) {
    this.defineReplacements.put(defineName, value);
  }

  public void setDefineToStringLiteral(String defineName, String value) {
    this.defineReplacements.put(defineName, value);
  }

  public void setDefineToNumberLiteral(String defineName, int value) {
    this.defineReplacements.put(defineName, value);
  }

  public void setDefineToDoubleLiteral(String defineName, double value) {
    this.defineReplacements.put(defineName, value);
  }

  public void skipAllCompilerPasses() {
    skipNonTranspilationPasses = true;
  }

  boolean enables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.TRUE;
  }

  boolean disables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.FALSE;
  }

  public void setWarningLevel(DiagnosticGroup type, CheckLevel level) {
    addWarningsGuard(new DiagnosticGroupWarningsGuard(type, level));
  }

  WarningsGuard getWarningsGuard() {
    return this.warningsGuard;
  }

  public void resetWarningsGuard() {
    this.warningsGuard = new ComposeWarningsGuard();
  }

  public void addWarningsGuard(WarningsGuard guard) {
    this.warningsGuard.addGuard(guard);
  }

  public void setRenamingPolicy(
      VariableRenamingPolicy newVariablePolicy, PropertyRenamingPolicy newPropertyPolicy) {
    this.variableRenaming = newVariablePolicy;
    this.propertyRenaming = newPropertyPolicy;
  }

  public void setReplaceIdGenerators(boolean replaceIdGenerators) {
    this.replaceIdGenerators = replaceIdGenerators;
  }

  public void setIdGenerators(Set<String> idGenerators) {
    RenamingMap gen = new UniqueRenamingToken();
    ImmutableMap.Builder<String, RenamingMap> builder = ImmutableMap.builder();
    for (String name : idGenerators) {
      builder.put(name, gen);
    }
    this.idGenerators = builder.buildOrThrow();
  }

  public void setIdGenerators(Map<String, RenamingMap> idGenerators) {
    this.idGenerators = ImmutableMap.copyOf(idGenerators);
  }

  public void setIdGeneratorsMap(String previousMappings) {
    this.idGeneratorsMapSerialized = previousMappings;
  }

  public void setXidHashFunction(Xid.HashFunction xidHashFunction) {
    this.xidHashFunction = xidHashFunction;
  }

  private Reach inlineFunctionsLevel;

  @Deprecated
  public void setInlineFunctions(boolean inlineFunctions) {
    this.setInlineFunctions(inlineFunctions ? Reach.ALL : Reach.NONE);
  }

  public void setInlineFunctions(Reach reach) {
    this.inlineFunctionsLevel = reach;
  }

  public Reach getInlineFunctionsLevel() {
    return this.inlineFunctionsLevel;
  }

  public void setMaxFunctionSizeAfterInlining(int funAstSize) {
    checkArgument(funAstSize > 0);
    this.maxFunctionSizeAfterInlining = funAstSize;
  }

  public void setInlineVariables(boolean inlineVariables) {
    this.inlineVariables = inlineVariables;
  }

  public void setInlineVariables(Reach reach) {
    switch (reach) {
      case ALL:
        this.inlineVariables = true;
        this.inlineLocalVariables = true;
        break;
      case LOCAL_ONLY:
        this.inlineVariables = false;
        this.inlineLocalVariables = true;
        break;
      case NONE:
        this.inlineVariables = false;
        this.inlineLocalVariables = false;
        break;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  public void setInlineProperties(boolean enable) {
    inlineProperties = enable;
  }

  public boolean shouldInlineProperties() {
    return inlineProperties;
  }

  public void setRemoveUnusedVariables(Reach reach) {
    switch (reach) {
      case ALL:
        this.removeUnusedVars = true;
        this.removeUnusedLocalVars = true;
        break;
      case LOCAL_ONLY:
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = true;
        break;
      case NONE:
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = false;
        break;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  public void setReplaceStringsConfiguration(
      String placeholderToken, List<String> functionDescriptors) {
    this.replaceStringsPlaceholderToken = placeholderToken;
    this.replaceStringsFunctionDescriptions = new ArrayList<>(functionDescriptors);
  }

  public void setRemoveAbstractMethods(boolean remove) {
    this.removeAbstractMethods = remove;
  }

  public void setRemoveClosureAsserts(boolean remove) {
    this.removeClosureAsserts = remove;
  }

  public void setRemoveJ2clAsserts(boolean remove) {
    this.removeJ2clAsserts = remove;
  }

  public void setColorizeErrorOutput(boolean colorizeErrorOutput) {
    this.colorizeErrorOutput = colorizeErrorOutput;
  }

  public boolean shouldColorizeErrorOutput() {
    return colorizeErrorOutput;
  }

  public void enableRuntimeTypeCheck(String logFunction) {
    this.runtimeTypeCheck = true;
    this.runtimeTypeCheckLogFunction = logFunction;
  }

  public void disableRuntimeTypeCheck() {
    this.runtimeTypeCheck = false;
  }

  public void setChecksOnly(boolean checksOnly) {
    this.checksOnly = checksOnly;
  }

  public void setOutputJs(OutputJs outputJs) {
    this.outputJs = outputJs;
  }

  public void setGenerateExports(boolean generateExports) {
    this.generateExports = generateExports;
  }

  public void setExportLocalPropertyDefinitions(boolean export) {
    this.exportLocalPropertyDefinitions = export;
  }

  public boolean shouldExportLocalPropertyDefinitions() {
    return this.exportLocalPropertyDefinitions;
  }

  public void setAngularPass(boolean angularPass) {
    this.angularPass = angularPass;
  }

  public void setPolymerVersion(Integer polymerVersion) {
    checkArgument(
        polymerVersion == null || polymerVersion == 1 || polymerVersion == 2,
        "Invalid Polymer version:",
        polymerVersion);
    this.polymerVersion = polymerVersion;
  }

  public void setPolymerExportPolicy(PolymerExportPolicy polymerExportPolicy) {
    this.polymerExportPolicy = polymerExportPolicy;
  }

  public void setChromePass(boolean chromePass) {
    this.chromePass = chromePass;
  }

  public boolean isChromePassEnabled() {
    return chromePass;
  }

  public void setJ2clPass(J2clPassMode j2clPassMode) {
    this.j2clPassMode = j2clPassMode;
  }

  public void setJ2clMinifierEnabled(boolean enabled) {
    this.j2clMinifierEnabled = enabled;
  }

  public void setJ2clMinifierPruningManifest(String j2clMinifierPruningManifest) {
    this.j2clMinifierPruningManifest = j2clMinifierPruningManifest;
  }

  public void setCodingConvention(CodingConvention codingConvention) {
    this.codingConvention = codingConvention;
  }

  public CodingConvention getCodingConvention() {
    return codingConvention;
  }

  public void setDependencyOptions(DependencyOptions dependencyOptions) {
    this.dependencyOptions = dependencyOptions;
  }

  public DependencyOptions getDependencyOptions() {
    return dependencyOptions;
  }

  public void setSummaryDetailLevel(int summaryDetailLevel) {
    this.summaryDetailLevel = summaryDetailLevel;
  }

  public void setExtraAnnotationNames(Iterable<String> extraAnnotationNames) {
    this.extraAnnotationNames = ImmutableSet.copyOf(extraAnnotationNames);
  }

  public boolean isExternExportsEnabled() {
    return externExports;
  }

  public void setOutputCharset(Charset charset) {
    this.outputCharset = charset;
  }

  Charset getOutputCharset() {
    return outputCharset;
  }

  public void setTweakProcessing(TweakProcessing tweakProcessing) {
    this.tweakProcessing = tweakProcessing;
  }

  public TweakProcessing getTweakProcessing() {
    return tweakProcessing;
  }

  public void setLanguage(LanguageMode language) {
    checkState(language != LanguageMode.NO_TRANSPILE);
    this.setLanguageIn(language);
    this.setLanguageOut(language);
  }

  public void setLanguageIn(LanguageMode languageIn) {
    checkState(languageIn != LanguageMode.NO_TRANSPILE);
    this.languageIn = languageIn == LanguageMode.STABLE ? LanguageMode.STABLE_IN : languageIn;
  }

  public LanguageMode getLanguageIn() {
    return languageIn;
  }

  public void setLanguageOut(LanguageMode languageOut) {
    if (languageOut == LanguageMode.NO_TRANSPILE) {
      languageOutIsDefaultStrict = Optional.absent();
      outputFeatureSet = Optional.absent();
    } else {
      languageOut = languageOut == LanguageMode.STABLE ? LanguageMode.STABLE_OUT : languageOut;
      languageOutIsDefaultStrict = Optional.of(languageOut.isDefaultStrict());
      setOutputFeatureSet(languageOut.toFeatureSet());
    }
  }

  public void setOutputFeatureSet(FeatureSet featureSet) {
    this.outputFeatureSet = Optional.of(featureSet);
  }

  public FeatureSet getOutputFeatureSet() {
    if (outputFeatureSet.isPresent()) {
      return outputFeatureSet.get();
    }

    return languageIn.toFeatureSet();
  }

  void setSkipUnsupportedPasses(boolean skipUnsupportedPasses) {
    this.skipUnsupportedPasses = skipUnsupportedPasses;
  }

  boolean shouldSkipUnsupportedPasses() {
    return skipUnsupportedPasses;
  }

  public boolean needsTranspilationFrom(FeatureSet languageLevel) {
    return getLanguageIn().toFeatureSet().contains(languageLevel)
        && !getOutputFeatureSet().contains(languageLevel);
  }

  public boolean needsTranspilationOf(FeatureSet.Feature feature) {
    return getLanguageIn().toFeatureSet().has(feature) && !getOutputFeatureSet().has(feature);
  }

  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public Environment getEnvironment() {
    return environment;
  }

  public void setAliasTransformationHandler(AliasTransformationHandler changes) {
    this.aliasHandler = changes;
  }

  public AliasTransformationHandler getAliasTransformationHandler() {
    return this.aliasHandler;
  }

  public void setErrorHandler(ErrorHandler handler) {
    this.errorHandler = handler;
  }

  public void setInferTypes(boolean enable) {
    inferTypes = enable;
  }

  public boolean getInferTypes() {
    return inferTypes;
  }

  @Deprecated
  public void setNewTypeInference(boolean enable) {}

  private boolean allowZoneJsWithAsyncFunctionsInOutput;

  public void setAllowZoneJsWithAsyncFunctionsInOutput(boolean enable) {
    this.allowZoneJsWithAsyncFunctionsInOutput = enable;
  }

  boolean allowsZoneJsWithAsyncFunctionsInOutput() {
    return this.checksOnly || this.allowZoneJsWithAsyncFunctionsInOutput;
  }

  public boolean isTypecheckingEnabled() {
    return this.checkTypes;
  }

  public boolean assumeStrictThis() {
    return assumeStrictThis;
  }

  public void setAssumeStrictThis(boolean enable) {
    this.assumeStrictThis = enable;
  }

  public boolean assumeClosuresOnlyCaptureReferences() {
    return assumeClosuresOnlyCaptureReferences;
  }

  public void setAssumeClosuresOnlyCaptureReferences(boolean enable) {
    this.assumeClosuresOnlyCaptureReferences = enable;
  }

  public void setPropertiesThatMustDisambiguate(Set<String> names) {
    this.propertiesThatMustDisambiguate = ImmutableSet.copyOf(names);
  }

  public ImmutableSet<String> getPropertiesThatMustDisambiguate() {
    return this.propertiesThatMustDisambiguate;
  }

  public void setPreserveDetailedSourceInfo(boolean preserveDetailedSourceInfo) {
    this.preserveDetailedSourceInfo = preserveDetailedSourceInfo;
  }

  boolean preservesDetailedSourceInfo() {
    return preserveDetailedSourceInfo;
  }

  public void setPreserveNonJSDocComments(boolean preserveNonJSDocComments) {
    this.preserveNonJSDocComments = preserveNonJSDocComments;
  }

  boolean getPreserveNonJSDocComments() {
    return preserveNonJSDocComments;
  }

  public void setContinueAfterErrors(boolean continueAfterErrors) {
    this.continueAfterErrors = continueAfterErrors;
  }

  boolean canContinueAfterErrors() {
    return continueAfterErrors;
  }

  public void setParseJsDocDocumentation(Config.JsDocParsing parseJsDocDocumentation) {
    this.parseJsDocDocumentation = parseJsDocDocumentation;
  }

  public Config.JsDocParsing isParseJsDocDocumentation() {
    return this.parseJsDocDocumentation;
  }

  public void setSkipNonTranspilationPasses(boolean skipNonTranspilationPasses) {
    this.skipNonTranspilationPasses = skipNonTranspilationPasses;
  }

  public void setDevMode(DevMode devMode) {
    this.devMode = devMode;
  }

  public void setCheckDeterminism(boolean checkDeterminism) {
    this.checkDeterminism = checkDeterminism;
  }

  public boolean getCheckDeterminism() {
    return checkDeterminism;
  }

  public void setMessageBundle(MessageBundle messageBundle) {
    this.messageBundle = messageBundle;
  }

  public void setCheckSymbols(boolean checkSymbols) {
    this.checkSymbols = checkSymbols;
  }

  public void setCheckSuspiciousCode(boolean checkSuspiciousCode) {
    this.checkSuspiciousCode = checkSuspiciousCode;
  }

  public void setCheckTypes(boolean checkTypes) {
    this.checkTypes = checkTypes;
  }

  public void setFoldConstants(boolean foldConstants) {
    this.foldConstants = foldConstants;
  }

  public void setDeadAssignmentElimination(boolean deadAssignmentElimination) {
    this.deadAssignmentElimination = deadAssignmentElimination;
  }

  public void setInlineConstantVars(boolean inlineConstantVars) {
    this.inlineConstantVars = inlineConstantVars;
  }

  public void setCrossChunkCodeMotion(boolean crossChunkCodeMotion) {
    this.crossChunkCodeMotion = crossChunkCodeMotion;
  }

  public void setCrossChunkCodeMotionNoStubMethods(boolean crossChunkCodeMotionNoStubMethods) {
    this.crossChunkCodeMotionNoStubMethods = crossChunkCodeMotionNoStubMethods;
  }

  public void setParentChunkCanSeeSymbolsDeclaredInChildren(
      boolean parentChunkCanSeeSymbolsDeclaredInChildren) {
    this.parentChunkCanSeeSymbolsDeclaredInChildren = parentChunkCanSeeSymbolsDeclaredInChildren;
  }

  public void setCrossChunkMethodMotion(boolean crossChunkMethodMotion) {
    this.crossChunkMethodMotion = crossChunkMethodMotion;
  }

  public void setCoalesceVariableNames(boolean coalesceVariableNames) {
    this.coalesceVariableNames = coalesceVariableNames;
  }

  public void setInlineLocalVariables(boolean inlineLocalVariables) {
    this.inlineLocalVariables = inlineLocalVariables;
  }

  public void setFlowSensitiveInlineVariables(boolean enabled) {
    this.flowSensitiveInlineVariables = enabled;
  }

  public void setSmartNameRemoval(boolean smartNameRemoval) {
    this.smartNameRemoval = smartNameRemoval;
    if (smartNameRemoval) {
      this.removeUnusedVars = true;
      this.removeUnusedPrototypeProperties = true;
    }
  }

  public void setRemoveDeadCode(boolean removeDeadCode) {
    this.removeDeadCode = removeDeadCode;
  }

  public void setExtractPrototypeMemberDeclarations(boolean enabled) {
    this.extractPrototypeMemberDeclarations =
        enabled
            ? ExtractPrototypeMemberDeclarationsMode.USE_GLOBAL_TEMP
            : ExtractPrototypeMemberDeclarationsMode.OFF;
  }

  public void setExtractPrototypeMemberDeclarations(ExtractPrototypeMemberDeclarationsMode mode) {
    this.extractPrototypeMemberDeclarations = mode;
  }

  public void setRemoveUnusedPrototypeProperties(boolean enabled) {
    this.removeUnusedPrototypeProperties = enabled;
    this.inlineGetters = enabled;
  }

  public void setCollapseVariableDeclarations(boolean enabled) {
    this.collapseVariableDeclarations = enabled;
  }

  public void setCollapseAnonymousFunctions(boolean enabled) {
    this.collapseAnonymousFunctions = enabled;
  }

  public void setAliasStringsMode(AliasStringsMode aliasStringsMode) {
    this.aliasStringsMode = aliasStringsMode;
  }

  public AliasStringsMode getAliasStringsMode() {
    return this.aliasStringsMode;
  }

  public void setOutputJsStringUsage(boolean outputJsStringUsage) {
    this.outputJsStringUsage = outputJsStringUsage;
  }

  public void setConvertToDottedProperties(boolean convertToDottedProperties) {
    this.convertToDottedProperties = convertToDottedProperties;
  }

  public void setUseTypesForLocalOptimization(boolean useTypesForLocalOptimization) {
    this.useTypesForLocalOptimization = useTypesForLocalOptimization;
  }

  public boolean shouldUseTypesForLocalOptimization() {
    return this.useTypesForLocalOptimization;
  }

  @Deprecated
  public void setUseTypesForOptimization(boolean useTypesForOptimization) {
    if (useTypesForOptimization) {
      this.disambiguateProperties = useTypesForOptimization;
      this.ambiguateProperties = useTypesForOptimization;
      this.inlineProperties = useTypesForOptimization;
      this.useTypesForLocalOptimization = useTypesForOptimization;
    }
  }

  public void setRewriteFunctionExpressions(boolean rewriteFunctionExpressions) {
    this.rewriteFunctionExpressions = rewriteFunctionExpressions;
  }

  public void setOptimizeCalls(boolean optimizeCalls) {
    this.optimizeCalls = optimizeCalls;
  }

  public boolean getOptimizeESClassConstructors() {
    return this.optimizeESClassConstructors;
  }

  public void setOptimizeESClassConstructors(boolean optimizeESClassConstructors) {
    this.optimizeESClassConstructors = optimizeESClassConstructors;
  }

  public void setOptimizeArgumentsArray(boolean optimizeArgumentsArray) {
    this.optimizeArgumentsArray = optimizeArgumentsArray;
  }

  public void setVariableRenaming(VariableRenamingPolicy variableRenaming) {
    this.variableRenaming = variableRenaming;
  }

  public void setPropertyRenaming(PropertyRenamingPolicy propertyRenaming) {
    this.propertyRenaming = propertyRenaming;
  }

  public PropertyRenamingPolicy getPropertyRenaming() {
    return this.propertyRenaming;
  }

  public void setLabelRenaming(boolean labelRenaming) {
    this.labelRenaming = labelRenaming;
  }

  public void setReserveRawExports(boolean reserveRawExports) {
    this.reserveRawExports = reserveRawExports;
  }

  public void setPreferStableNames(boolean preferStableNames) {
    this.preferStableNames = preferStableNames;
  }

  public void setGeneratePseudoNames(boolean generatePseudoNames) {
    this.generatePseudoNames = generatePseudoNames;
  }

  public void setRenamePrefix(String renamePrefix) {
    this.renamePrefix = renamePrefix;
  }

  public String getRenamePrefixNamespace() {
    return this.renamePrefixNamespace;
  }

  public void setRenamePrefixNamespace(String renamePrefixNamespace) {
    this.renamePrefixNamespace = renamePrefixNamespace;
  }

  public void setCollapsePropertiesLevel(PropertyCollapseLevel level) {
    this.collapsePropertiesLevel = level;
  }

  @Deprecated
  public void setCollapseProperties(boolean fullyCollapse) {
    this.collapsePropertiesLevel =
        fullyCollapse ? PropertyCollapseLevel.ALL : PropertyCollapseLevel.NONE;
  }

  public void setDevirtualizeMethods(boolean devirtualizeMethods) {
    this.devirtualizeMethods = devirtualizeMethods;
  }

  public void setComputeFunctionSideEffects(boolean computeFunctionSideEffects) {
    this.computeFunctionSideEffects = computeFunctionSideEffects;
  }

  public void setDisambiguateProperties(boolean disambiguateProperties) {
    this.disambiguateProperties = disambiguateProperties;
  }

  public boolean shouldDisambiguateProperties() {
    return this.disambiguateProperties;
  }

  public void setAmbiguateProperties(boolean ambiguateProperties) {
    this.ambiguateProperties = ambiguateProperties;
  }

  public boolean shouldAmbiguateProperties() {
    return this.ambiguateProperties;
  }

  public void setInputVariableMap(VariableMap inputVariableMap) {
    this.inputVariableMap = inputVariableMap;
  }

  public void setInputPropertyMap(VariableMap inputPropertyMap) {
    this.inputPropertyMap = inputPropertyMap;
  }

  public void setExportTestFunctions(boolean exportTestFunctions) {
    this.exportTestFunctions = exportTestFunctions;
  }

  public void setRuntimeTypeCheck(boolean runtimeTypeCheck) {
    this.runtimeTypeCheck = runtimeTypeCheck;
  }

  public void setRuntimeTypeCheckLogFunction(String runtimeTypeCheckLogFunction) {
    this.runtimeTypeCheckLogFunction = runtimeTypeCheckLogFunction;
  }

  public void setSyntheticBlockStartMarker(String syntheticBlockStartMarker) {
    this.syntheticBlockStartMarker = syntheticBlockStartMarker;
  }

  public void setSyntheticBlockEndMarker(String syntheticBlockEndMarker) {
    this.syntheticBlockEndMarker = syntheticBlockEndMarker;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public void setDoLateLocalization(boolean doLateLocalization) {
    this.doLateLocalization = doLateLocalization;
  }

  public boolean doLateLocalization() {
    return doLateLocalization;
  }

  public boolean shouldRunReplaceMessagesPass() {
    return !shouldRunReplaceMessagesForChrome() && messageBundle != null;
  }

  public void setMarkAsCompiled(boolean markAsCompiled) {
    this.markAsCompiled = markAsCompiled;
  }

  public void setClosurePass(boolean closurePass) {
    this.closurePass = closurePass;
  }

  public void setPreserveClosurePrimitives(boolean preserveClosurePrimitives) {
    this.preserveClosurePrimitives = preserveClosurePrimitives;
  }

  public boolean shouldPreservesGoogProvidesAndRequires() {
    return this.preserveClosurePrimitives;
  }

  public boolean shouldPreserveGoogModule() {
    return this.preserveClosurePrimitives;
  }

  public boolean shouldPreserveGoogLibraryPrimitives() {
    return this.preserveClosurePrimitives;
  }

  public void setPreserveTypeAnnotations(boolean preserveTypeAnnotations) {
    this.preserveTypeAnnotations = preserveTypeAnnotations;
  }

  public void setGatherCssNames(boolean gatherCssNames) {
    this.gatherCssNames = gatherCssNames;
  }

  @Deprecated
  public void setStripTypes(Set<String> stripTypes) {
    this.stripTypes = ImmutableSet.copyOf(stripTypes);
  }

  @Deprecated
  public ImmutableSet<String> getStripTypes() {
    return this.stripTypes;
  }

  @Deprecated
  public void setStripNameSuffixes(Set<String> stripNameSuffixes) {
    this.stripNameSuffixes = ImmutableSet.copyOf(stripNameSuffixes);
  }

  @Deprecated
  public void setStripNamePrefixes(Set<String> stripNamePrefixes) {
    this.stripNamePrefixes = ImmutableSet.copyOf(stripNamePrefixes);
  }

  public void addCustomPass(CustomPassExecutionTime time, CompilerPass customPass) {
    if (customPasses == null) {
      customPasses = LinkedHashMultimap.create();
    }
    customPasses.put(time, customPass);
  }

  public void setDefineReplacements(Map<String, Object> defineReplacements) {
    this.defineReplacements.clear();
    this.defineReplacements.putAll(defineReplacements);
  }

  @Deprecated
  public void setMoveFunctionDeclarations(boolean moveFunctionDeclarations) {
    setRewriteGlobalDeclarationsForTryCatchWrapping(moveFunctionDeclarations);
  }

  public void setRewriteGlobalDeclarationsForTryCatchWrapping(boolean rewrite) {
    this.rewriteGlobalDeclarationsForTryCatchWrapping = rewrite;
  }

  public void setCssRenamingMap(CssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
  }

  @Deprecated
  public void setCssRenamingWhitelist(Set<String> skiplist) {
    setCssRenamingSkiplist(skiplist);
  }

  public void setCssRenamingSkiplist(Set<String> skiplist) {
    this.cssRenamingSkiplist = skiplist;
  }

  public void setReplaceStringsFunctionDescriptions(
      List<String> replaceStringsFunctionDescriptions) {
    this.replaceStringsFunctionDescriptions = replaceStringsFunctionDescriptions;
  }

  public void setReplaceStringsPlaceholderToken(String replaceStringsPlaceholderToken) {
    this.replaceStringsPlaceholderToken = replaceStringsPlaceholderToken;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  public boolean isPrettyPrint() {
    return this.prettyPrint;
  }

  public void setLineBreak(boolean lineBreak) {
    this.lineBreak = lineBreak;
  }

  public boolean getPreferLineBreakAtEndOfFile() {
    return this.preferLineBreakAtEndOfFile;
  }

  public void setPreferLineBreakAtEndOfFile(boolean lineBreakAtEnd) {
    this.preferLineBreakAtEndOfFile = lineBreakAtEnd;
  }

  public void setPrintInputDelimiter(boolean printInputDelimiter) {
    this.printInputDelimiter = printInputDelimiter;
  }

  public void setInputDelimiter(String inputDelimiter) {
    this.inputDelimiter = inputDelimiter;
  }

  public void setDebugLogDirectory(@Nullable Path dir) {
    this.debugLogDirectory = dir;
  }

  @Nullable
  public Path getDebugLogDirectory() {
    return debugLogDirectory;
  }

  public void setQuoteKeywordProperties(boolean quoteKeywordProperties) {
    this.quoteKeywordProperties = quoteKeywordProperties;
  }

  public boolean shouldQuoteKeywordProperties() {
    if (incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS) {
      return false;
    }
    return this.quoteKeywordProperties || FeatureSet.ES3.contains(getOutputFeatureSet());
  }

  public void setErrorFormat(ErrorFormat errorFormat) {
    this.errorFormat = errorFormat;
  }

  public ErrorFormat getErrorFormat() {
    return this.errorFormat;
  }

  public void setWarningsGuard(ComposeWarningsGuard warningsGuard) {
    this.warningsGuard = warningsGuard;
  }

  public void setLineLengthThreshold(int lineLengthThreshold) {
    this.lineLengthThreshold = lineLengthThreshold;
  }

  public int getLineLengthThreshold() {
    return this.lineLengthThreshold;
  }

  public void setUseOriginalNamesInOutput(boolean useOriginalNamesInOutput) {
    this.useOriginalNamesInOutput = useOriginalNamesInOutput;
  }

  public boolean getUseOriginalNamesInOutput() {
    return this.useOriginalNamesInOutput;
  }

  public void setExternExports(boolean externExports) {
    this.externExports = externExports;
  }

  public void setExternExportsPath(String externExportsPath) {
    this.externExportsPath = externExportsPath;
  }

  public void setSourceMapOutputPath(String sourceMapOutputPath) {
    this.sourceMapOutputPath = sourceMapOutputPath;
  }

  public void setApplyInputSourceMaps(boolean applyInputSourceMaps) {
    this.applyInputSourceMaps = applyInputSourceMaps;
  }

  public void setResolveSourceMapAnnotations(boolean resolveSourceMapAnnotations) {
    this.resolveSourceMapAnnotations = resolveSourceMapAnnotations;
  }

  public void setSourceMapIncludeSourcesContent(boolean sourceMapIncludeSourcesContent) {
    this.sourceMapIncludeSourcesContent = sourceMapIncludeSourcesContent;
  }

  public void setParseInlineSourceMaps(boolean parseInlineSourceMaps) {
    this.parseInlineSourceMaps = parseInlineSourceMaps;
  }

  public void setSourceMapDetailLevel(SourceMap.DetailLevel sourceMapDetailLevel) {
    this.sourceMapDetailLevel = sourceMapDetailLevel;
  }

  public void setSourceMapFormat(SourceMap.Format sourceMapFormat) {
    this.sourceMapFormat = sourceMapFormat;
  }

  public void setSourceMapLocationMappings(
      List<? extends SourceMap.LocationMapping> sourceMapLocationMappings) {
    this.sourceMapLocationMappings = sourceMapLocationMappings;
  }

  public void setTransformAMDToCJSModules(boolean transformAMDToCJSModules) {
    this.transformAMDToCJSModules = transformAMDToCJSModules;
  }

  public void setProcessCommonJSModules(boolean processCommonJSModules) {
    this.processCommonJSModules = processCommonJSModules;
  }

  public boolean getProcessCommonJSModules() {
    return processCommonJSModules;
  }

  public enum Es6ModuleTranspilation {
    NONE,

    RELATIVIZE_IMPORT_PATHS,

    TO_COMMON_JS_LIKE_MODULES,

    COMPILE
  }

  private Es6ModuleTranspilation es6ModuleTranspilation = Es6ModuleTranspilation.COMPILE;

  public void setEs6ModuleTranspilation(Es6ModuleTranspilation value) {
    es6ModuleTranspilation = value;
  }

  public Es6ModuleTranspilation getEs6ModuleTranspilation() {
    return es6ModuleTranspilation;
  }

  public void setCommonJSModulePathPrefix(String commonJSModulePathPrefix) {
    setModuleRoots(ImmutableList.of(commonJSModulePathPrefix));
  }

  public void setModuleRoots(List<String> moduleRoots) {
    this.moduleRoots = moduleRoots;
  }

  public void setRewritePolyfills(boolean rewritePolyfills) {
    this.rewritePolyfills = rewritePolyfills;
  }

  public boolean getRewritePolyfills() {
    return this.rewritePolyfills;
  }

  public void setIsolatePolyfills(boolean isolatePolyfills) {
    this.isolatePolyfills = isolatePolyfills;
    if (this.isolatePolyfills) {
      this.setDefineToBooleanLiteral("$jscomp.ISOLATE_POLYFILLS", isolatePolyfills);
    }
  }

  public boolean getIsolatePolyfills() {
    return this.isolatePolyfills;
  }

  public void setForceLibraryInjection(Iterable<String> libraries) {
    this.forceLibraryInjection = ImmutableList.copyOf(libraries);
  }

  public void setPreventLibraryInjection(boolean preventLibraryInjection) {
    this.preventLibraryInjection = preventLibraryInjection;
  }

  public void setUnusedImportsToRemove(@Nullable ImmutableSet<String> unusedImportsToRemove) {
    this.unusedImportsToRemove = unusedImportsToRemove;
  }

  @Nullable
  public ImmutableSet<String> getUnusedImportsToRemove() {
    return this.unusedImportsToRemove;
  }

  public void setInstrumentForCoverageOption(InstrumentOption instrumentForCoverageOption) {
    this.instrumentForCoverageOption = checkNotNull(instrumentForCoverageOption);
  }

  public InstrumentOption getInstrumentForCoverageOption() {
    return this.instrumentForCoverageOption;
  }

  public void setProductionInstrumentationArrayName(String productionInstrumentationArrayName) {
    this.productionInstrumentationArrayName = checkNotNull(productionInstrumentationArrayName);
  }

  public String getProductionInstrumentationArrayName() {
    return this.productionInstrumentationArrayName;
  }

  public final ImmutableList<ConformanceConfig> getConformanceConfigs() {
    return conformanceConfigs;
  }

  @GwtIncompatible("Conformance")
  public void setConformanceConfig(ConformanceConfig conformanceConfig) {
    setConformanceConfigs(ImmutableList.of(conformanceConfig));
  }

  @GwtIncompatible("Conformance")
  public void setConformanceConfigs(List<ConformanceConfig> configs) {
    this.conformanceConfigs =
        ImmutableList.<ConformanceConfig>builder()
            .add(ResourceLoader.loadGlobalConformance(CompilerOptions.class))
            .addAll(configs)
            .build();
  }

  public void clearConformanceConfigs() {
    this.conformanceConfigs = ImmutableList.of();
  }

  public boolean shouldEmitUseStrict() {
    return this.emitUseStrict.or(languageOutIsDefaultStrict).or(languageIn.isDefaultStrict());
  }

  public CompilerOptions setEmitUseStrict(boolean emitUseStrict) {
    this.emitUseStrict = Optional.of(emitUseStrict);
    return this;
  }

  public ResolutionMode getModuleResolutionMode() {
    return this.moduleResolutionMode;
  }

  public void setModuleResolutionMode(ResolutionMode moduleResolutionMode) {
    this.moduleResolutionMode = moduleResolutionMode;
  }

  public ImmutableMap<String, String> getBrowserResolverPrefixReplacements() {
    return this.browserResolverPrefixReplacements;
  }

  public void setBrowserResolverPrefixReplacements(
      ImmutableMap<String, String> browserResolverPrefixReplacements) {
    this.browserResolverPrefixReplacements = browserResolverPrefixReplacements;
  }

  public void setPathEscaper(ModuleLoader.PathEscaper pathEscaper) {
    this.pathEscaper = pathEscaper;
  }

  public ModuleLoader.PathEscaper getPathEscaper() {
    return pathEscaper;
  }

  public List<String> getPackageJsonEntryNames() {
    return this.packageJsonEntryNames;
  }

  public void setPackageJsonEntryNames(List<String> names) {
    this.packageJsonEntryNames = names;
  }

  public void setUseSizeHeuristicToStopOptimizationLoop(boolean mayStopEarly) {
    this.useSizeHeuristicToStopOptimizationLoop = mayStopEarly;
  }

  public void setMaxOptimizationLoopIterations(int maxIterations) {
    this.optimizationLoopMaxIterations = maxIterations;
  }

  public ChunkOutputType getChunkOutputType() {
    return chunkOutputType;
  }

  public void setChunkOutputType(ChunkOutputType chunkOutputType) {
    this.chunkOutputType = chunkOutputType;
  }

  @GwtIncompatible("ObjectOutputStream")
  public void serialize(OutputStream objectOutputStream) throws IOException {
    new java.io.ObjectOutputStream(objectOutputStream).writeObject(this);
  }

  @GwtIncompatible("ObjectInputStream")
  public static CompilerOptions deserialize(InputStream objectInputStream)
      throws IOException, ClassNotFoundException {
    return (CompilerOptions) new java.io.ObjectInputStream(objectInputStream).readObject();
  }

  public void setStrictMessageReplacement(boolean strictMessageReplacement) {
    this.strictMessageReplacement = strictMessageReplacement;
  }

  public boolean getStrictMessageReplacement() {
    return this.strictMessageReplacement;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("aliasStringsMode", getAliasStringsMode())
        .add("aliasHandler", getAliasTransformationHandler())
        .add("ambiguateProperties", ambiguateProperties)
        .add("angularPass", angularPass)
        .add("assumeClosuresOnlyCaptureReferences", assumeClosuresOnlyCaptureReferences)
        .add("assumeGettersArePure", assumeGettersArePure)
        .add("assumeStrictThis", assumeStrictThis())
        .add("browserResolverPrefixReplacements", browserResolverPrefixReplacements)
        .add("checkDeterminism", getCheckDeterminism())
        .add("checksOnly", checksOnly)
        .add("checkSuspiciousCode", checkSuspiciousCode)
        .add("checkSymbols", checkSymbols)
        .add("checkTypes", checkTypes)
        .add("closurePass", closurePass)
        .add("coalesceVariableNames", coalesceVariableNames)
        .add("codingConvention", getCodingConvention())
        .add("collapseAnonymousFunctions", collapseAnonymousFunctions)
        .add("collapseObjectLiterals", collapseObjectLiterals)
        .add("collapseProperties", collapsePropertiesLevel)
        .add("collapseVariableDeclarations", collapseVariableDeclarations)
        .add("colorizeErrorOutput", shouldColorizeErrorOutput())
        .add("computeFunctionSideEffects", computeFunctionSideEffects)
        .add("conformanceConfigs", getConformanceConfigs())
        .add("conformanceRemoveRegexFromPath", conformanceRemoveRegexFromPath)
        .add("continueAfterErrors", canContinueAfterErrors())
        .add("convertToDottedProperties", convertToDottedProperties)
        .add("crossChunkCodeMotion", crossChunkCodeMotion)
        .add("crossChunkCodeMotionNoStubMethods", crossChunkCodeMotionNoStubMethods)
        .add("crossChunkMethodMotion", crossChunkMethodMotion)
        .add("cssRenamingMap", cssRenamingMap)
        .add("cssRenamingSkiplist", cssRenamingSkiplist)
        .add("customPasses", customPasses)
        .add("deadAssignmentElimination", deadAssignmentElimination)
        .add("debugLogDirectory", debugLogDirectory)
        .add("defineReplacements", getDefineReplacements())
        .add("dependencyOptions", getDependencyOptions())
        .add("devirtualizeMethods", devirtualizeMethods)
        .add("devMode", devMode)
        .add("disambiguateProperties", disambiguateProperties)
        .add("enableModuleRewriting", enableModuleRewriting)
        .add("environment", getEnvironment())
        .add("errorFormat", errorFormat)
        .add("errorHandler", errorHandler)
        .add("es6ModuleTranspilation", es6ModuleTranspilation)
        .add("exportLocalPropertyDefinitions", exportLocalPropertyDefinitions)
        .add("exportTestFunctions", exportTestFunctions)
        .add("externExports", isExternExportsEnabled())
        .add("externExportsPath", externExportsPath)
        .add("extraAnnotationNames", extraAnnotationNames)
        .add("extractPrototypeMemberDeclarations", extractPrototypeMemberDeclarations)
        .add("filesToPrintAfterEachPassRegexList", filesToPrintAfterEachPassRegexList)
        .add("flowSensitiveInlineVariables", flowSensitiveInlineVariables)
        .add("foldConstants", foldConstants)
        .add("forceLibraryInjection", forceLibraryInjection)
        .add("gatherCssNames", gatherCssNames)
        .add("generateExports", generateExports)
        .add("generatePseudoNames", generatePseudoNames)
        .add("generateTypedExterns", shouldGenerateTypedExterns())
        .add("idGenerators", idGenerators)
        .add("idGeneratorsMapSerialized", idGeneratorsMapSerialized)
        .add("incrementalCheckMode", incrementalCheckMode)
        .add("inferConsts", inferConsts)
        .add("inferTypes", inferTypes)
        .add("inlineConstantVars", inlineConstantVars)
        .add("inlineFunctionsLevel", inlineFunctionsLevel)
        .add("inlineGetters", inlineGetters)
        .add("inlineLocalVariables", inlineLocalVariables)
        .add("inlineProperties", inlineProperties)
        .add("inlineVariables", inlineVariables)
        .add("inputDelimiter", inputDelimiter)
        .add("inputPropertyMap", inputPropertyMap)
        .add("inputSourceMaps", inputSourceMaps)
        .add("inputVariableMap", inputVariableMap)
        .add("instrumentForCoverageOnly", instrumentForCoverageOnly)
        .add("instrumentForCoverageOption", instrumentForCoverageOption.toString())
        .add("productionInstrumentationArrayName", productionInstrumentationArrayName)
        .add("isolatePolyfills", isolatePolyfills)
        .add("j2clMinifierEnabled", j2clMinifierEnabled)
        .add("j2clMinifierPruningManifest", j2clMinifierPruningManifest)
        .add("j2clPassMode", j2clPassMode)
        .add("labelRenaming", labelRenaming)
        .add("languageIn", getLanguageIn())
        .add("languageOutIsDefaultStrict", languageOutIsDefaultStrict)
        .add("lineBreak", lineBreak)
        .add("lineLengthThreshold", lineLengthThreshold)
        .add("locale", locale)
        .add("markAsCompiled", markAsCompiled)
        .add("maxFunctionSizeAfterInlining", maxFunctionSizeAfterInlining)
        .add("messageBundle", messageBundle)
        .add("moduleRoots", moduleRoots)
        .add("chunksToPrintAfterEachPassRegexList", chunksToPrintAfterEachPassRegexList)
        .add("qnameUsesToPrintAfterEachPassRegexList", qnameUsesToPrintAfterEachPassList)
        .add(
            "rewriteGlobalDeclarationsForTryCatchWrapping",
            rewriteGlobalDeclarationsForTryCatchWrapping)
        .add("nameGenerator", nameGenerator)
        .add("optimizeArgumentsArray", optimizeArgumentsArray)
        .add("optimizeCalls", optimizeCalls)
        .add("optimizeESClassConstructors", optimizeESClassConstructors)
        .add("outputCharset", outputCharset)
        .add("outputFeatureSet", outputFeatureSet)
        .add("outputJs", outputJs)
        .add("outputJsStringUsage", outputJsStringUsage)
        .add(
            "parentChunkCanSeeSymbolsDeclaredInChildren",
            parentChunkCanSeeSymbolsDeclaredInChildren)
        .add("parseJsDocDocumentation", isParseJsDocDocumentation())
        .add("pathEscaper", pathEscaper)
        .add("polymerVersion", polymerVersion)
        .add("polymerExportPolicy", polymerExportPolicy)
        .add("preferLineBreakAtEndOfFile", preferLineBreakAtEndOfFile)
        .add("preferSingleQuotes", preferSingleQuotes)
        .add("preferStableNames", preferStableNames)
        .add("preserveDetailedSourceInfo", preservesDetailedSourceInfo())
        .add("preserveNonJSDocComments", getPreserveNonJSDocComments())
        .add("preserveGoogProvidesAndRequires", preserveClosurePrimitives)
        .add("preserveTypeAnnotations", preserveTypeAnnotations)
        .add("prettyPrint", prettyPrint)
        .add("preventLibraryInjection", preventLibraryInjection)
        .add("printConfig", printConfig)
        .add("printInputDelimiter", printInputDelimiter)
        .add("printSourceAfterEachPass", printSourceAfterEachPass)
        .add("processCommonJSModules", processCommonJSModules)
        .add("propertiesThatMustDisambiguate", propertiesThatMustDisambiguate)
        .add("propertyRenaming", propertyRenaming)
        .add("protectHiddenSideEffects", protectHiddenSideEffects)
        .add("quoteKeywordProperties", quoteKeywordProperties)
        .add("removeAbstractMethods", removeAbstractMethods)
        .add("removeClosureAsserts", removeClosureAsserts)
        .add("removeJ2clAsserts", removeJ2clAsserts)
        .add("removeDeadCode", removeDeadCode)
        .add("removeUnusedClassProperties", removeUnusedClassProperties)
        .add("removeUnusedConstructorProperties", removeUnusedConstructorProperties)
        .add("removeUnusedLocalVars", removeUnusedLocalVars)
        .add("removeUnusedPrototypeProperties", removeUnusedPrototypeProperties)
        .add("removeUnusedVars", removeUnusedVars)
        .add(
            "renamePrefixNamespaceAssumeCrossChunkNames",
            renamePrefixNamespaceAssumeCrossChunkNames)
        .add("renamePrefixNamespace", renamePrefixNamespace)
        .add("renamePrefix", renamePrefix)
        .add("replaceIdGenerators", replaceIdGenerators)
        .add("replaceMessagesWithChromeI18n", replaceMessagesWithChromeI18n)
        .add("replaceStringsFunctionDescriptions", replaceStringsFunctionDescriptions)
        .add("replaceStringsPlaceholderToken", replaceStringsPlaceholderToken)
        .add("reserveRawExports", reserveRawExports)
        .add("rewriteFunctionExpressions", rewriteFunctionExpressions)
        .add("rewritePolyfills", rewritePolyfills)
        .add("runtimeTypeCheckLogFunction", runtimeTypeCheckLogFunction)
        .add("runtimeTypeCheck", runtimeTypeCheck)
        .add("rewriteModulesBeforeTypechecking", rewriteModulesBeforeTypechecking)
        .add("skipNonTranspilationPasses", skipNonTranspilationPasses)
        .add("smartNameRemoval", smartNameRemoval)
        .add("sourceMapDetailLevel", sourceMapDetailLevel)
        .add("sourceMapFormat", sourceMapFormat)
        .add("sourceMapLocationMappings", sourceMapLocationMappings)
        .add("sourceMapOutputPath", sourceMapOutputPath)
        .add("strictMessageReplacement", strictMessageReplacement)
        .add("stripNamePrefixes", stripNamePrefixes)
        .add("stripNameSuffixes", stripNameSuffixes)
        .add("stripTypes", stripTypes)
        .add("summaryDetailLevel", summaryDetailLevel)
        .add("syntheticBlockEndMarker", syntheticBlockEndMarker)
        .add("syntheticBlockStartMarker", syntheticBlockStartMarker)
        .add("tcProjectId", tcProjectId)
        .add("tracer", tracer)
        .add("transformAMDToCJSModules", transformAMDToCJSModules)
        .add("trustedStrings", trustedStrings)
        .add("tweakProcessing", getTweakProcessing())
        .add("emitUseStrict", emitUseStrict)
        .add("useTypesForLocalOptimization", useTypesForLocalOptimization)
        .add("unusedImportsToRemove", unusedImportsToRemove)
        .add("variableRenaming", variableRenaming)
        .add("warningsGuard", getWarningsGuard())
        .add("wrapGoogModulesForWhitespaceOnly", wrapGoogModulesForWhitespaceOnly)
        .toString();
  }

  public enum InstrumentOption {
    NONE, LINE_ONLY, BRANCH_ONLY, PRODUCTION; public static InstrumentOption fromString(String value) {
      if (value == null) {
        return null;
      }
      switch (value) {
        case "NONE":
          return InstrumentOption.NONE;
        case "LINE":
          return InstrumentOption.LINE_ONLY;
        case "BRANCH":
          return InstrumentOption.BRANCH_ONLY;
        case "PRODUCTION":
          return InstrumentOption.PRODUCTION;
        default:
          return null;
      }
    }
  }

  public enum ChunkOutputType {
    GLOBAL_NAMESPACE,
    ES_MODULES;
  }

  public enum LanguageMode {
    ECMASCRIPT3,

    ECMASCRIPT5,

    ECMASCRIPT5_STRICT,

    ECMASCRIPT_2015,

    ECMASCRIPT_2016,

    ECMASCRIPT_2017,

    ECMASCRIPT_2018,

    ECMASCRIPT_2019,

    ECMASCRIPT_2020,

    ECMASCRIPT_2021,

    ECMASCRIPT_NEXT,

    ECMASCRIPT_NEXT_IN,

    STABLE,

    NO_TRANSPILE,

    UNSUPPORTED;

    public static final LanguageMode STABLE_IN = ECMASCRIPT_2021;
    public static final LanguageMode STABLE_OUT = ECMASCRIPT5;

    boolean isDefaultStrict() {
      switch (this) {
        case ECMASCRIPT3:
        case ECMASCRIPT5:
          return false;
        default:
          return true;
      }
    }

    public static LanguageMode fromString(String value) {
      if (value == null) {
        return null;
      }
      String canonicalizedName = Ascii.toUpperCase(value.trim()).replaceFirst("^ES", "ECMASCRIPT");

      if (canonicalizedName.equals("ECMASCRIPT6")
          || canonicalizedName.equals("ECMASCRIPT6_STRICT")) {
        return ECMASCRIPT_2015;
      }

      try {
        return LanguageMode.valueOf(canonicalizedName);
      } catch (IllegalArgumentException e) {
        return null; }
    }

    public FeatureSet toFeatureSet() {
      switch (this) {
        case ECMASCRIPT3:
          return FeatureSet.ES3;
        case ECMASCRIPT5:
        case ECMASCRIPT5_STRICT:
          return FeatureSet.ES5;
        case ECMASCRIPT_2015:
          return FeatureSet.ES2015_MODULES;
        case ECMASCRIPT_2016:
          return FeatureSet.ES2016_MODULES;
        case ECMASCRIPT_2017:
          return FeatureSet.ES2017_MODULES;
        case ECMASCRIPT_2018:
          return FeatureSet.ES2018_MODULES;
        case ECMASCRIPT_2019:
          return FeatureSet.ES2019_MODULES;
        case ECMASCRIPT_2020:
          return FeatureSet.ES2020_MODULES;
        case ECMASCRIPT_2021:
          return FeatureSet.ES2021_MODULES;
        case ECMASCRIPT_NEXT:
          return FeatureSet.ES_NEXT;
        case NO_TRANSPILE:
        case ECMASCRIPT_NEXT_IN:
          return FeatureSet.ES_NEXT_IN;
        case UNSUPPORTED:
          return FeatureSet.ES_UNSUPPORTED;
        case STABLE:
          throw new UnsupportedOperationException(
              "STABLE has different feature sets for language in and out. "
                  + "Use STABLE_IN or STABLE_OUT.");
      }
      throw new IllegalStateException();
    }
  }

  public static enum DevMode {
    OFF,

    START,

    START_AND_END,

    EVERY_PASS
  }

  public static enum TracerMode {
    ALL, RAW_SIZE, AST_SIZE, TIMING_ONLY, OFF; public boolean isOn() {
      return this != OFF;
    }
  }

  public static enum TweakProcessing {
    OFF, CHECK, STRIP; public boolean isOn() {
      return this != OFF;
    }

    public boolean shouldStrip() {
      return this == STRIP;
    }
  }

  public static enum IsolationMode {
    NONE, IIFE; 

  public static enum AliasStringsMode {
    NONE, LARGE, ALL 

  public interface AliasTransformationHandler {

    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position);
  }

  public interface AliasTransformation {

    void addAlias(String alias, String definition);
  }

  static final AliasTransformationHandler NULL_ALIAS_TRANSFORMATION_HANDLER =
      new NullAliasTransformationHandler();

  private static class NullAliasTransformationHandler implements AliasTransformationHandler {
    private static final AliasTransformation NULL_ALIAS_TRANSFORMATION =
        new NullAliasTransformation();

    @Override
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position) {
      position.setItem(NULL_ALIAS_TRANSFORMATION);
      return NULL_ALIAS_TRANSFORMATION;
    }

    private static class NullAliasTransformation implements AliasTransformation {
      @Override
      public void addAlias(String alias, String definition) {}
    }
  }

  public static enum Environment {
    BROWSER,

    CUSTOM
  }

  static enum JsonStreamMode {
    NONE,

    IN,

    OUT,

    BOTH
  }

  public static enum J2clPassMode {
    OFF,
    AUTO;

    boolean shouldAddJ2clPasses() {
      return this == AUTO;
    }
  }

  public boolean expectStrictModeInput() {
    return isStrictModeInput.or(getLanguageIn().isDefaultStrict());
  }

  public CompilerOptions setStrictModeInput(boolean isStrictModeInput) {
    this.isStrictModeInput = Optional.of(isStrictModeInput);
    return this;
  }

  public char[] getPropertyReservedNamingFirstChars() {
    char[] reservedChars = null;
    if (polymerVersion != null && polymerVersion > 1) {
      if (reservedChars == null) {
        reservedChars = POLYMER_PROPERTY_RESERVED_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, POLYMER_PROPERTY_RESERVED_FIRST_CHARS);
      }
    } else if (angularPass) {
      if (reservedChars == null) {
        reservedChars = ANGULAR_PROPERTY_RESERVED_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, ANGULAR_PROPERTY_RESERVED_FIRST_CHARS);
      }
    }
    return reservedChars;
  }

  public char[] getPropertyReservedNamingNonFirstChars() {
    char[] reservedChars = null;
    if (polymerVersion != null && polymerVersion > 1) {
      if (reservedChars == null) {
        reservedChars = POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS);
      }
    }
    return reservedChars;
  }

  @GwtIncompatible("ObjectOutputStream")
  private void writeObject(ObjectOutputStream out) throws IOException, ClassNotFoundException {
    out.defaultWriteObject();
    out.writeObject(outputCharset == null ? null : outputCharset.name());
  }

  @GwtIncompatible("ObjectInputStream")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    String outputCharsetName = (String) in.readObject();
    if (outputCharsetName != null) {
      outputCharset = Charset.forName(outputCharsetName);
    }
  }

  boolean shouldOptimize() {
    return !skipNonTranspilationPasses
        && !checksOnly
        && !shouldGenerateTypedExterns()
        && !instrumentForCoverageOnly;
  }
}
