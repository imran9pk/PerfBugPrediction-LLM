package com.google.devtools.build.lib.rules.cpp;


import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.MutableActionGraph.ActionConflictException;
import com.google.devtools.build.lib.analysis.AnalysisUtils;
import com.google.devtools.build.lib.analysis.ConfiguredAspect;
import com.google.devtools.build.lib.analysis.ConfiguredAspectFactory;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.packages.AspectDefinition;
import com.google.devtools.build.lib.packages.AspectParameters;
import com.google.devtools.build.lib.packages.NativeAspectClass;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.skyframe.ConfiguredTargetAndData;
import javax.annotation.Nullable;

public final class GraphNodeAspect extends NativeAspectClass implements ConfiguredAspectFactory {
  public static final Function<Rule, AspectParameters> ASPECT_PARAMETERS =
      new Function<Rule, AspectParameters>() {
        @Nullable
        @Override
        public AspectParameters apply(Rule rule) {
          return rule.isAttributeValueExplicitlySpecified("dynamic_deps")
              ? AspectParameters.EMPTY
              : null;
        }
      };

  @Override
  public AspectDefinition getDefinition(AspectParameters aspectParameters) {
    return new AspectDefinition.Builder(this).propagateAlongAllAttributes().build();
  }

  @Override
  public ConfiguredAspect create(
      ConfiguredTargetAndData ctadBase,
      RuleContext ruleContext,
      AspectParameters params,
      String toolsRepository)
      throws ActionConflictException {
    ImmutableList.Builder<GraphNodeInfo> children = ImmutableList.builder();
    if (ruleContext.attributes().has("deps")) {
      children.addAll(
          AnalysisUtils.getProviders(ruleContext.getPrerequisites("deps"), GraphNodeInfo.class));
    }
    return new ConfiguredAspect.Builder(ruleContext)
        .addProvider(
            GraphNodeInfo.class, new GraphNodeInfo(ruleContext.getLabel(), children.build()))
        .build();
  }
}
