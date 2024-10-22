package com.google.devtools.build.lib.analysis;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.MiddlemanFactory;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import java.util.List;

public final class CompilationHelper {
  public static NestedSet<Artifact> getAggregatingMiddleman(
      RuleContext ruleContext, String purpose, NestedSet<Artifact> filesToBuild) {
    return NestedSetBuilder.wrap(Order.STABLE_ORDER, getMiddlemanInternal(
        ruleContext.getAnalysisEnvironment(), ruleContext, ruleContext.getActionOwner(), purpose,
        filesToBuild));
  }

  private static List<Artifact> getMiddlemanInternal(AnalysisEnvironment env,
      RuleContext ruleContext, ActionOwner actionOwner, String purpose,
      NestedSet<Artifact> filesToBuild) {
    if (filesToBuild == null) {
      return ImmutableList.of();
    }
    MiddlemanFactory factory = env.getMiddlemanFactory();
    return ImmutableList.of(
        factory.createMiddlemanAllowMultiple(
            env,
            actionOwner,
            ruleContext.getPackageDirectory(),
            purpose,
            filesToBuild,
            ruleContext.getMiddlemanDirectory()));
  }

  public static NestedSet<Artifact> getAggregatingMiddleman(
      RuleContext ruleContext, String purpose, TransitiveInfoCollection dep) {
    return NestedSetBuilder.wrap(Order.STABLE_ORDER, getMiddlemanInternal(
        ruleContext.getAnalysisEnvironment(), ruleContext, ruleContext.getActionOwner(), purpose,
        dep));
  }

  private static List<Artifact> getMiddlemanInternal(AnalysisEnvironment env,
      RuleContext ruleContext, ActionOwner actionOwner, String purpose,
      TransitiveInfoCollection dep) {
    if (dep == null) {
      return ImmutableList.of();
    }
    MiddlemanFactory factory = env.getMiddlemanFactory();
    NestedSet<Artifact> artifacts = dep.getProvider(FileProvider.class).getFilesToBuild();
    return ImmutableList.of(
        factory.createMiddlemanAllowMultiple(
            env,
            actionOwner,
            ruleContext.getPackageDirectory(),
            purpose,
            artifacts,
            ruleContext.getMiddlemanDirectory()));
  }
}
