package com.google.devtools.build.lib.concurrent;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class Sharder<T> implements Iterable<List<T>> {
  private final List<List<T>> shards;
  private int nextShard = 0;

  public Sharder(int maxNumShards, int expectedTotalSize) {
    Preconditions.checkArgument(maxNumShards > 0);
    Preconditions.checkArgument(expectedTotalSize >= 0);
    this.shards = immutableListOfLists(maxNumShards, expectedTotalSize / maxNumShards);
  }

  public void add(T item) {
    shards.get(nextShard).add(item);
    nextShard = (nextShard + 1) % shards.size();
  }

  private static <T> List<List<T>> immutableListOfLists(int numLists, int expectedSize) {
    List<List<T>> list = Lists.newArrayListWithCapacity(numLists);
    for (int i = 0; i < numLists; i++) {
      list.add(Lists.<T>newArrayListWithExpectedSize(expectedSize));
    }
    return Collections.unmodifiableList(list);
  }

  @Override
  public Iterator<List<T>> iterator() {
    return Iterables.filter(shards, list -> !list.isEmpty()).iterator();
  }
}
