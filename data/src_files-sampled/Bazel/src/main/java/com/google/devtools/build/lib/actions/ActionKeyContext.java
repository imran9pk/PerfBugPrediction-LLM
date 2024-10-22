package com.google.devtools.build.lib.actions;

import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetFingerprintCache;
import com.google.devtools.build.lib.util.Fingerprint;

public class ActionKeyContext {

  private final NestedSetFingerprintCache nestedSetFingerprintCache =
      new NestedSetFingerprintCache();

  public <T> void addNestedSetToFingerprint(Fingerprint fingerprint, NestedSet<T> nestedSet)
      throws CommandLineExpansionException, InterruptedException {
    nestedSetFingerprintCache.addNestedSetToFingerprint(fingerprint, nestedSet);
  }

  public <T> void addNestedSetToFingerprint(
      CommandLineItem.MapFn<? super T> mapFn, Fingerprint fingerprint, NestedSet<T> nestedSet)
      throws CommandLineExpansionException, InterruptedException {
    nestedSetFingerprintCache.addNestedSetToFingerprint(mapFn, fingerprint, nestedSet);
  }

  public void clear() {
    nestedSetFingerprintCache.clear();
  }
}
