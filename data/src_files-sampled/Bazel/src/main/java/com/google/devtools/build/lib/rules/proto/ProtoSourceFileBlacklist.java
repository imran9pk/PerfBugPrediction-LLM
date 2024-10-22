package com.google.devtools.build.lib.rules.proto;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.config.ExecutionTransitionFactory;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.List;

public class ProtoSourceFileBlacklist {
  private static final PathFragment BAZEL_TOOLS_PREFIX =
      PathFragment.create("external/bazel_tools/");
  private final RuleContext ruleContext;
  private final ImmutableSet<PathFragment> blacklistProtoFilePaths;

  public ProtoSourceFileBlacklist(
      RuleContext ruleContext, NestedSet<Artifact> blacklistProtoFiles) {
    this.ruleContext = ruleContext;
    ImmutableSet.Builder<PathFragment> blacklistProtoFilePathsBuilder =
        new ImmutableSet.Builder<>();
    for (Artifact blacklistProtoFile : blacklistProtoFiles.toList()) {
      PathFragment execPath = blacklistProtoFile.getExecPath();
      if (execPath.startsWith(BAZEL_TOOLS_PREFIX)) {
        blacklistProtoFilePathsBuilder.add(execPath.relativeTo(BAZEL_TOOLS_PREFIX));
      } else {
        blacklistProtoFilePathsBuilder.add(execPath);
      }
    }
    blacklistProtoFilePaths = blacklistProtoFilePathsBuilder.build();
  }

  public Iterable<Artifact> filter(Iterable<Artifact> protoFiles) {
    return Streams.stream(protoFiles).filter(f -> !isBlacklisted(f)).collect(toImmutableSet());
  }

  public boolean checkSrcs(Iterable<Artifact> protoFiles, String topLevelProtoRuleName) {
    List<Artifact> blacklisted = new ArrayList<>();
    List<Artifact> nonBlacklisted = new ArrayList<>();
    for (Artifact protoFile : protoFiles) {
      if (isBlacklisted(protoFile)) {
        blacklisted.add(protoFile);
      } else {
        nonBlacklisted.add(protoFile);
      }
    }
    if (!nonBlacklisted.isEmpty() && !blacklisted.isEmpty()) {
      ruleContext.attributeError(
          "srcs",
          createBlacklistedProtosMixError(
              Artifact.toRootRelativePaths(blacklisted),
              Artifact.toRootRelativePaths(nonBlacklisted),
              ruleContext.getLabel().toString(),
              topLevelProtoRuleName));
    }

    return blacklisted.isEmpty();
  }

  public boolean isBlacklisted(Artifact protoFile) {
    return blacklistProtoFilePaths.contains(protoFile.getExecPath());
  }

  public static Attribute.Builder<List<Label>> blacklistFilegroupAttribute(
      String attributeName, List<Label> blacklistFileGroups) {
    return attr(attributeName, LABEL_LIST)
        .cfg(ExecutionTransitionFactory.create())
        .value(blacklistFileGroups);
  }

  @VisibleForTesting
  public static String createBlacklistedProtosMixError(
      Iterable<String> blacklisted, Iterable<String> nonBlacklisted, String protoLibraryRuleLabel,
      String topLevelProtoRuleName) {
    return String.format(
        "The 'srcs' attribute of '%s' contains protos for which '%s' "
            + "shouldn't generate code (%s), in addition to protos for which it should (%s).\n"
            + "Separate '%1$s' into 2 proto_library rules.",
        protoLibraryRuleLabel,
        topLevelProtoRuleName,
        Joiner.on(", ").join(blacklisted),
        Joiner.on(", ").join(nonBlacklisted));
  }
}
