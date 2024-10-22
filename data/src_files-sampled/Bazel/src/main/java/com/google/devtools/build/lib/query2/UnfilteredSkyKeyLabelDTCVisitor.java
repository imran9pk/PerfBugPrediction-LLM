package com.google.devtools.build.lib.query2;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.query2.ParallelVisitorUtils.QueryVisitorFactory;
import com.google.devtools.build.lib.query2.engine.QueryUtil.AggregateAllCallback;
import com.google.devtools.build.lib.query2.engine.Uniquifier;
import com.google.devtools.build.skyframe.SkyKey;

class UnfilteredSkyKeyLabelDTCVisitor extends AbstractUnfilteredLabelDTCVisitor<SkyKey> {
  private UnfilteredSkyKeyLabelDTCVisitor(
      SkyQueryEnvironment env,
      Uniquifier<SkyKey> uniquifier,
      int processResultsBatchSize,
      AggregateAllCallback<SkyKey, ImmutableSet<SkyKey>> aggregateAllCallback) {
    super(
        env,
        uniquifier,
        processResultsBatchSize,
        aggregateAllCallback);
  }

  @Override
  protected Iterable<SkyKey> outputKeysToOutputValues(Iterable<SkyKey> targetKeys) {
    return targetKeys;
  }

  static class Factory implements QueryVisitorFactory<SkyKey, SkyKey, SkyKey> {
    private final SkyQueryEnvironment env;
    private final Uniquifier<SkyKey> uniquifier;
    private final AggregateAllCallback<SkyKey, ImmutableSet<SkyKey>> aggregateAllCallback;
    private final int processResultsBatchSize;

    Factory(
        SkyQueryEnvironment env,
        Uniquifier<SkyKey> uniquifier,
        int processResultsBatchSize,
        AggregateAllCallback<SkyKey, ImmutableSet<SkyKey>> aggregateAllCallback) {
      this.env = env;
      this.uniquifier = uniquifier;
      this.processResultsBatchSize = processResultsBatchSize;
      this.aggregateAllCallback = aggregateAllCallback;
    }

    @Override
    public UnfilteredSkyKeyLabelDTCVisitor create() {
      return new UnfilteredSkyKeyLabelDTCVisitor(
          env, uniquifier, processResultsBatchSize, aggregateAllCallback);
    }
  }
}
