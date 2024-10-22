package com.google.devtools.build.lib.bazel.rules.android;

import com.google.devtools.build.lib.bazel.rules.cpp.BazelCppSemantics;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaSemantics;
import com.google.devtools.build.lib.rules.android.AndroidBinary;
import com.google.devtools.build.lib.rules.android.AndroidSemantics;
import com.google.devtools.build.lib.rules.cpp.CppSemantics;
import com.google.devtools.build.lib.rules.java.JavaSemantics;

public class BazelAndroidBinary extends AndroidBinary {
  @Override
  protected JavaSemantics createJavaSemantics() {
    return BazelJavaSemantics.INSTANCE;
  }

  @Override
  protected AndroidSemantics createAndroidSemantics() {
    return BazelAndroidSemantics.INSTANCE;
  }

  @Override
  protected CppSemantics createCppSemantics() {
    return BazelCppSemantics.INSTANCE;
  }
}
