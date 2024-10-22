package com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.r13;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.NdkPaths;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.StlImpl;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CToolchain;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CrosstoolRelease;
import java.util.ArrayList;
import java.util.List;

final class AndroidNdkCrosstoolsR13 {

  static CrosstoolRelease create(
      NdkPaths ndkPaths, StlImpl stlImpl, String hostPlatform, String clangVersion) {
    return CrosstoolRelease.newBuilder()
        .setMajorVersion("android")
        .setMinorVersion("")
        .setDefaultTargetCpu("armeabi")
        .addAllToolchain(createToolchains(ndkPaths, stlImpl, hostPlatform, clangVersion))
        .build();
  }

  private static ImmutableList<CToolchain> createToolchains(
      NdkPaths ndkPaths, StlImpl stlImpl, String hostPlatform, String clangVersion) {

    List<CToolchain.Builder> toolchainBuilders = new ArrayList<>();
    toolchainBuilders.addAll(new ArmCrosstools(ndkPaths, stlImpl, clangVersion).createCrosstools());
    toolchainBuilders.addAll(
        new MipsCrosstools(ndkPaths, stlImpl, clangVersion).createCrosstools());
    toolchainBuilders.addAll(new X86Crosstools(ndkPaths, stlImpl, clangVersion).createCrosstools());

    ImmutableList.Builder<CToolchain> toolchains = new ImmutableList.Builder<>();

    for (CToolchain.Builder toolchainBuilder : toolchainBuilders) {
      toolchainBuilder
          .setHostSystemName(hostPlatform)
          .setTargetLibc("local")
          .setAbiVersion(toolchainBuilder.getTargetCpu())
          .setAbiLibcVersion("local");

      toolchainBuilder.addCxxBuiltinIncludeDirectory("%sysroot%/usr/include");

      toolchains.add(toolchainBuilder.build());
    }

    return toolchains.build();
  }
}
