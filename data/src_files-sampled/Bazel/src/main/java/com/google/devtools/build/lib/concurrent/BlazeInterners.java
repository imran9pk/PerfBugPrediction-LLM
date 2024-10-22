package com.google.devtools.build.lib.concurrent;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Interners.InternerBuilder;

public class BlazeInterners {
  private static final int DEFAULT_CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();
  private static final int CONCURRENCY_LEVEL;

  static {
    String val = System.getenv("BLAZE_INTERNER_CONCURRENCY_LEVEL");
    CONCURRENCY_LEVEL = (val == null) ? DEFAULT_CONCURRENCY_LEVEL : Integer.parseInt(val);
  }

  public static int concurrencyLevel() {
    return CONCURRENCY_LEVEL;
  }

  private static InternerBuilder setConcurrencyLevel(InternerBuilder builder) {
    return builder.concurrencyLevel(CONCURRENCY_LEVEL);
  }

  public static <T> Interner<T> newWeakInterner() {
    return setConcurrencyLevel(Interners.newBuilder().weak()).build();
  }

  public static <T> Interner<T> newStrongInterner() {
    return setConcurrencyLevel(Interners.newBuilder().strong()).build();
  }
}

