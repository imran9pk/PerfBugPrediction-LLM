package io.crate.execution.engine.distribution;

import io.crate.common.annotations.VisibleForTesting;
import io.crate.data.BatchIterator;
import io.crate.data.Paging;
import io.crate.data.Row;
import io.crate.data.RowConsumer;
import io.crate.exceptions.SQLExceptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributingConsumer implements RowConsumer {

    private static final Logger LOGGER = LogManager.getLogger(DistributingConsumer.class);
    private final Executor responseExecutor;
    private final UUID jobId;
    private final int targetPhaseId;
    private final byte inputId;
    private final int bucketIdx;
    private final TransportDistributedResultAction distributedResultAction;
    private final int pageSize;
    private final StreamBucket[] buckets;
    private final List<Downstream> downstreams;
    private final boolean traceEnabled;
    private final CompletableFuture<Void> completionFuture;

    @VisibleForTesting
    final MultiBucketBuilder multiBucketBuilder;

    private volatile Throwable failure;

    public DistributingConsumer(Executor responseExecutor,
                                UUID jobId,
                                MultiBucketBuilder multiBucketBuilder,
                                int targetPhaseId,
                                byte inputId,
                                int bucketIdx,
                                Collection<String> downstreamNodeIds,
                                TransportDistributedResultAction distributedResultAction,
                                int pageSize) {
        this.traceEnabled = LOGGER.isTraceEnabled();
        this.responseExecutor = responseExecutor;
        this.jobId = jobId;
        this.multiBucketBuilder = multiBucketBuilder;
        this.targetPhaseId = targetPhaseId;
        this.inputId = inputId;
        this.bucketIdx = bucketIdx;
        this.distributedResultAction = distributedResultAction;
        this.pageSize = pageSize;
        this.buckets = new StreamBucket[downstreamNodeIds.size()];
        this.completionFuture = new CompletableFuture<>();
        downstreams = new ArrayList<>(downstreamNodeIds.size());
        for (String downstreamNodeId : downstreamNodeIds) {
            downstreams.add(new Downstream(downstreamNodeId));
        }
    }

    @Override
    public void accept(BatchIterator<Row> iterator, @Nullable Throwable failure) {
        if (failure == null) {
            consumeIt(iterator);
        } else {
            completionFuture.completeExceptionally(failure);
            forwardFailure(null, failure);
        }
    }

    @Override
    public CompletableFuture<?> completionFuture() {
        return completionFuture;
    }

    private void consumeIt(BatchIterator<Row> it) {
        try {
            while (it.moveNext()) {
                multiBucketBuilder.add(it.currentElement());
                if (multiBucketBuilder.size() >= pageSize || multiBucketBuilder.ramBytesUsed() >= Paging.MAX_PAGE_BYTES) {
                    forwardResults(it, false);
                    return;
                }
            }
            if (it.allLoaded()) {
                forwardResults(it, true);
            } else {
                it.loadNextBatch().whenComplete((r, t) -> {
                    if (t == null) {
                        consumeIt(it);
                    } else {
                        forwardFailure(it, t);
                    }
                });
            }
        } catch (Throwable t) {
            forwardFailure(it, t);
        }
    }

    private void forwardFailure(@Nullable final BatchIterator it, final Throwable f) {
        Throwable failure = SQLExceptions.unwrap(f); AtomicInteger numActiveRequests = new AtomicInteger(downstreams.size());
        DistributedResultRequest request =
            new DistributedResultRequest(jobId, targetPhaseId, inputId, bucketIdx, failure, false);
        for (int i = 0; i < downstreams.size(); i++) {
            Downstream downstream = downstreams.get(i);
            if (downstream.needsMoreData == false) {
                countdownAndMaybeCloseIt(numActiveRequests, it);
            } else {
                if (traceEnabled) {
                    LOGGER.trace("forwardFailure targetNode={} jobId={} targetPhase={}/{} bucket={} failure={}",
                        downstream.nodeId, jobId, targetPhaseId, inputId, bucketIdx, failure);
                }
                distributedResultAction.pushResult(downstream.nodeId, request, new ActionListener<>() {
                    @Override
                    public void onResponse(DistributedResultResponse response) {
                        downstream.needsMoreData = false;
                        countdownAndMaybeCloseIt(numActiveRequests, it);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (traceEnabled) {
                            LOGGER.trace(
                                "Error sending failure to downstream={} jobId={} targetPhase={}/{} bucket={} failure={}",
                                downstream.nodeId,
                                jobId,
                                targetPhaseId,
                                inputId,
                                bucketIdx,
                                e
                            );
                        }
                        countdownAndMaybeCloseIt(numActiveRequests, it);
                    }
                });
            }
        }
    }

    private void countdownAndMaybeCloseIt(AtomicInteger numActiveRequests, @Nullable BatchIterator it) {
        if (numActiveRequests.decrementAndGet() == 0) {
            if (it != null) {
                it.close();
                completionFuture.complete(null);
            }
        }
    }

    private void forwardResults(BatchIterator<Row> it, boolean isLast) {
        multiBucketBuilder.build(buckets);

        AtomicInteger numActiveRequests = new AtomicInteger(downstreams.size());
        for (int i = 0; i < downstreams.size(); i++) {
            Downstream downstream = downstreams.get(i);
            if (downstream.needsMoreData == false) {
                countdownAndMaybeContinue(it, numActiveRequests, true);
                continue;
            }
            if (traceEnabled) {
                LOGGER.trace("forwardResults targetNode={} jobId={} targetPhase={}/{} bucket={} isLast={}",
                    downstream.nodeId, jobId, targetPhaseId, inputId, bucketIdx, isLast);
            }
            distributedResultAction.pushResult(
                downstream.nodeId,
                new DistributedResultRequest(jobId, targetPhaseId, inputId, bucketIdx, buckets[i], isLast),
                new ActionListener<>() {
                    @Override
                    public void onResponse(DistributedResultResponse response) {
                        downstream.needsMoreData = response.needMore();
                        countdownAndMaybeContinue(it, numActiveRequests, false);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        failure = e;
                        downstream.needsMoreData = false;
                        countdownAndMaybeContinue(it, numActiveRequests, false);
                    }
                }
            );
        }
    }

    private void countdownAndMaybeContinue(BatchIterator<Row> it, AtomicInteger numActiveRequests, boolean sameExecutor) {
        if (numActiveRequests.decrementAndGet() == 0) {
            if (downstreams.stream().anyMatch(Downstream::needsMoreData)) {
                if (failure == null) {
                    if (sameExecutor) {
                        consumeIt(it);
                    } else {
                        try {
                            responseExecutor.execute(() -> consumeIt(it));
                        } catch (EsRejectedExecutionException e) {
                            failure = e;
                            forwardFailure(it, failure);
                        }
                    }
                } else {
                    forwardFailure(it, failure);
                }
            } else {
                it.close();
                completionFuture.complete(null);
            }
        }
    }

    private static class Downstream {

        private final String nodeId;
        private boolean needsMoreData = true;

        Downstream(String nodeId) {
            this.nodeId = nodeId;
        }

        boolean needsMoreData() {
            return needsMoreData;
        }

        @Override
        public String toString() {
            return "Downstream{" +
                   nodeId + '\'' +
                   ", needsMoreData=" + needsMoreData +
                   '}';
        }
    }

    @Override
    public String toString() {
        return "DistributingConsumer{" +
               "jobId=" + jobId +
               ", targetPhaseId=" + targetPhaseId +
               ", inputId=" + inputId +
               ", bucketIdx=" + bucketIdx +
               ", downstreams=" + downstreams +
               '}';
    }
}
