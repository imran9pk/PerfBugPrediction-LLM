package com.google.javascript.jscomp;

import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

public final class CompilerOptionsPreprocessor {

  static void preprocess(CompilerOptions options) {
    if (options.getInlineFunctionsLevel() == CompilerOptions.Reach.NONE
        && options.maxFunctionSizeAfterInlining
            != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING) {
      throw new InvalidOptionsException(
          "max_function_size_after_inlining has no effect if inlining is disabled.");
    }
  }

  public static class InvalidOptionsException extends RuntimeException {
    private InvalidOptionsException(String message, Object... args) {
      super(SimpleFormat.format(message, args));
    }
  }

  private CompilerOptionsPreprocessor() {}
}
