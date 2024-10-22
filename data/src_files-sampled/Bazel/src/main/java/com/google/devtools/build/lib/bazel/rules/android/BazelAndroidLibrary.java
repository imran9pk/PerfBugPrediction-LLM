package com.google.devtools.build.lib.bazel.rules.android;

import com.google.devtools.build.lib.bazel.rules.java.BazelJavaSemantics;
import com.google.devtools.build.lib.rules.android.AndroidLibrary;
import com.google.devtools.build.lib.rules.android.AndroidSemantics;
import com.google.devtools.build.lib.rules.java.JavaSemantics;

public class BazelAndroidLibrary extends AndroidLibrary {

  @Override
  protected JavaSemantics createJavaSemantics() {
    return BazelJavaSemantics.INSTANCE;
  }

  @Override
  protected AndroidSemantics createAndroidSemantics() {
    return BazelAndroidSemantics.INSTANCE;
  }
}
