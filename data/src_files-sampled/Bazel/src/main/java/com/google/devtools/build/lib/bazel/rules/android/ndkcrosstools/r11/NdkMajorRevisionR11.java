package com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.r11;

import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.ApiLevel;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.NdkMajorRevision;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.NdkPaths;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.StlImpl;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CrosstoolRelease;

public class NdkMajorRevisionR11 implements NdkMajorRevision {
  @Override
  public CrosstoolRelease crosstoolRelease(
      NdkPaths ndkPaths, StlImpl stlImpl, String hostPlatform) {
    return AndroidNdkCrosstoolsR11.create(ndkPaths, stlImpl, hostPlatform);
  }

  @Override
  public ApiLevel apiLevel(EventHandler eventHandler, String name, String apiLevel) {
    return new ApiLevelR11(eventHandler, name, apiLevel);
  }
}
