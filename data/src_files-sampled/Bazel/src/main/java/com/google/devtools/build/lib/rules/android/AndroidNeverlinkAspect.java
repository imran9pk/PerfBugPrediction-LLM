package com.google.devtools.build.lib.rules.android;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.MutableActionGraph.ActionConflictException;
import com.google.devtools.build.lib.analysis.ConfiguredAspect;
import com.google.devtools.build.lib.analysis.ConfiguredAspectFactory;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.AspectDefinition;
import com.google.devtools.build.lib.packages.AspectParameters;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.NativeAspectClass;
import com.google.devtools.build.lib.packages.StarlarkProviderIdentifier;
import com.google.devtools.build.lib.rules.java.JavaCommon;
import com.google.devtools.build.lib.rules.java.JavaInfo;
import com.google.devtools.build.lib.skyframe.ConfiguredTargetAndData;
import java.util.ArrayList;
import java.util.List;

public class AndroidNeverlinkAspect extends NativeAspectClass implements ConfiguredAspectFactory {
  public static final String NAME = "AndroidNeverlinkAspect";
  private static final ImmutableList<String> ATTRIBUTES =
      ImmutableList.of(
          "deps", "exports", "runtime_deps", "binary_under_test", "$instrumentation_test_runner");

  @Override
  public ConfiguredAspect create(
      ConfiguredTargetAndData ctadBase,
      RuleContext ruleContext,
      AspectParameters parameters,
      String toolsRepository)
      throws ActionConflictException {
    if (!JavaCommon.getConstraints(ruleContext).contains("android")
        && !ruleContext.getRule().getRuleClass().startsWith("android_")) {
      return new ConfiguredAspect.Builder(ruleContext).build();
    }

    List<TransitiveInfoCollection> deps = new ArrayList<>();

    for (String attribute : ATTRIBUTES) {
      if (!ruleContext.getRule().getRuleClassObject().hasAttr(attribute, BuildType.LABEL_LIST)) {
        continue;
      }

      deps.addAll(ruleContext.getPrerequisites(attribute));
    }

    NestedSetBuilder<Artifact> runtimeJars = NestedSetBuilder.naiveLinkOrder();
    runtimeJars.addAll(JavaInfo.getJavaInfo(ctadBase.getConfiguredTarget()).getDirectRuntimeJars());
    AndroidLibraryResourceClassJarProvider provider =
        AndroidLibraryResourceClassJarProvider.getProvider(ctadBase.getConfiguredTarget());
    if (provider != null) {
      runtimeJars.addTransitive(provider.getResourceClassJars());
    }
    return new ConfiguredAspect.Builder(ruleContext)
        .addProvider(
            AndroidNeverLinkLibrariesProvider.create(
                AndroidCommon.collectTransitiveNeverlinkLibraries(
                    ruleContext, deps, runtimeJars.build())))
        .build();
  }

  @Override
  public AspectDefinition getDefinition(AspectParameters aspectParameters) {
    AspectDefinition.Builder builder = new AspectDefinition.Builder(this);
    for (String attribute : ATTRIBUTES) {
      builder.propagateAlongAttribute(attribute);
    }

    return builder
        .requireStarlarkProviders(StarlarkProviderIdentifier.forKey(JavaInfo.PROVIDER.getKey()))
        .requireStarlarkProviders(
            StarlarkProviderIdentifier.forKey(JavaInfo.PROVIDER.getKey()),
            StarlarkProviderIdentifier.forKey(
                AndroidLibraryResourceClassJarProvider.PROVIDER.getKey()))
        .requiresConfigurationFragments()
        .build();
  }
}
