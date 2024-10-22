package com.google.javascript.jscomp;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.io.Serializable;

public final class UniqueIdSupplier implements Serializable {
  private final Multiset<Integer> counter;

  UniqueIdSupplier() {
    counter = HashMultiset.create();
  }

  public String getUniqueId(CompilerInput input) {
    String filePath = input.getSourceFile().getName();
    int fileHashCode = filePath.hashCode();
    int id = counter.add(fileHashCode, 1);
    String fileHashString = (fileHashCode < 0) ? ("m" + -fileHashCode) : ("" + fileHashCode);
    return fileHashString + "$" + id;
  }
}
