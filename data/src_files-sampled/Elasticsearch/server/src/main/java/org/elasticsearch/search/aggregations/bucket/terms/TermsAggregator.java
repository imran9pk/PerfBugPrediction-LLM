package org.elasticsearch.search.aggregations.bucket.terms;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.Comparators;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.InternalOrder;
import org.elasticsearch.search.aggregations.InternalOrder.Aggregation;
import org.elasticsearch.search.aggregations.InternalOrder.CompoundOrder;
import org.elasticsearch.search.aggregations.bucket.BucketsAggregator;
import org.elasticsearch.search.aggregations.bucket.DeferableBucketAggregator;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregator;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregator;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.AggregationPath;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class TermsAggregator extends DeferableBucketAggregator {

    public static class BucketCountThresholds implements Writeable, ToXContentFragment {
        private long minDocCount;
        private long shardMinDocCount;
        private int requiredSize;
        private int shardSize;

        public BucketCountThresholds(long minDocCount, long shardMinDocCount, int requiredSize, int shardSize) {
            this.minDocCount = minDocCount;
            this.shardMinDocCount = shardMinDocCount;
            this.requiredSize = requiredSize;
            this.shardSize = shardSize;
        }

        public BucketCountThresholds(StreamInput in) throws IOException {
            requiredSize = in.readInt();
            shardSize = in.readInt();
            minDocCount = in.readLong();
            shardMinDocCount = in.readLong();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeInt(requiredSize);
            out.writeInt(shardSize);
            out.writeLong(minDocCount);
            out.writeLong(shardMinDocCount);
        }

        public BucketCountThresholds(BucketCountThresholds bucketCountThresholds) {
            this(bucketCountThresholds.minDocCount, bucketCountThresholds.shardMinDocCount, bucketCountThresholds.requiredSize,
                    bucketCountThresholds.shardSize);
        }

        public void ensureValidity() {

            if (shardSize < requiredSize) {
                setShardSize(requiredSize);
            }

            if (shardMinDocCount > minDocCount) {
                setShardMinDocCount(minDocCount);
            }

            if (requiredSize <= 0 || shardSize <= 0) {
                throw new ElasticsearchException("parameters [required_size] and [shard_size] must be >0 in terms aggregation.");
            }

            if (minDocCount < 0 || shardMinDocCount < 0) {
                throw new ElasticsearchException("parameter [min_doc_count] and [shardMinDocCount] must be >=0 in terms aggregation.");
            }
        }

        public long getShardMinDocCount() {
            return shardMinDocCount;
        }

        public void setShardMinDocCount(long shardMinDocCount) {
            this.shardMinDocCount = shardMinDocCount;
        }

        public long getMinDocCount() {
            return minDocCount;
        }

        public void setMinDocCount(long minDocCount) {
            this.minDocCount = minDocCount;
        }

        public int getRequiredSize() {
            return requiredSize;
        }

        public void setRequiredSize(int requiredSize) {
            this.requiredSize = requiredSize;
        }

        public int getShardSize() {
            return shardSize;
        }

        public void setShardSize(int shardSize) {
            this.shardSize = shardSize;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field(TermsAggregationBuilder.REQUIRED_SIZE_FIELD_NAME.getPreferredName(), requiredSize);
            if (shardSize != -1) {
                builder.field(TermsAggregationBuilder.SHARD_SIZE_FIELD_NAME.getPreferredName(), shardSize);
            }
            builder.field(TermsAggregationBuilder.MIN_DOC_COUNT_FIELD_NAME.getPreferredName(), minDocCount);
            builder.field(TermsAggregationBuilder.SHARD_MIN_DOC_COUNT_FIELD_NAME.getPreferredName(), shardMinDocCount);
            return builder;
        }

        @Override
        public int hashCode() {
            return Objects.hash(requiredSize, shardSize, minDocCount, shardMinDocCount);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            BucketCountThresholds other = (BucketCountThresholds) obj;
            return Objects.equals(requiredSize, other.requiredSize)
                    && Objects.equals(shardSize, other.shardSize)
                    && Objects.equals(minDocCount, other.minDocCount)
                    && Objects.equals(shardMinDocCount, other.shardMinDocCount);
        }
    }

    protected final DocValueFormat format;
    protected final BucketCountThresholds bucketCountThresholds;
    protected final BucketOrder order;
    protected final Set<Aggregator> aggsUsedForSorting = new HashSet<>();
    protected final SubAggCollectionMode collectMode;

    public TermsAggregator(String name, AggregatorFactories factories, SearchContext context, Aggregator parent,
            BucketCountThresholds bucketCountThresholds, BucketOrder order, DocValueFormat format, SubAggCollectionMode collectMode,
            List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) throws IOException {
        super(name, factories, context, parent, pipelineAggregators, metaData);
        this.bucketCountThresholds = bucketCountThresholds;
        this.order = InternalOrder.validate(order, this);
        this.format = format;
        if (subAggsNeedScore() && descendsFromNestedAggregator(parent)) {
            this.collectMode = SubAggCollectionMode.DEPTH_FIRST;
        } else {
            this.collectMode = collectMode;
        }
        if (order instanceof Aggregation){
            AggregationPath path = ((Aggregation) order).path();
            aggsUsedForSorting.add(path.resolveTopmostAggregator(this));
        } else if (order instanceof CompoundOrder) {
            CompoundOrder compoundOrder = (CompoundOrder) order;
            for (BucketOrder orderElement : compoundOrder.orderElements()) {
                if (orderElement instanceof Aggregation) {
                    AggregationPath path = ((Aggregation) orderElement).path();
                    aggsUsedForSorting.add(path.resolveTopmostAggregator(this));
                }
            }
        }
    }

    static boolean descendsFromNestedAggregator(Aggregator parent) {
        while (parent != null) {
            if (parent.getClass() == NestedAggregator.class) {
                return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    private boolean subAggsNeedScore() {
        for (Aggregator subAgg : subAggregators) {
            if (subAgg.needsScores()) {
                return true;
            }
        }
        return false;
    }

    public Comparator<Bucket> bucketComparator(AggregationPath path, boolean asc) {

        final Aggregator aggregator = path.resolveAggregator(this);
        final String key = path.lastPathElement().key;

        if (aggregator instanceof SingleBucketAggregator) {
            assert key == null : "this should be picked up before the aggregation is executed - on validate";
            return (b1, b2) -> {
                int mul = asc ? 1 : -1;
                int v1 = ((SingleBucketAggregator) aggregator).bucketDocCount(((InternalTerms.Bucket) b1).bucketOrd);
                int v2 = ((SingleBucketAggregator) aggregator).bucketDocCount(((InternalTerms.Bucket) b2).bucketOrd);
                return mul * (v1 - v2);
            };
        }

        assert !(aggregator instanceof BucketsAggregator) : "this should be picked up before the aggregation is executed - on validate";

        if (aggregator instanceof NumericMetricsAggregator.MultiValue) {
            assert key != null : "this should be picked up before the aggregation is executed - on validate";
            return (b1, b2) -> {
                double v1 = ((NumericMetricsAggregator.MultiValue) aggregator).metric(key, ((InternalTerms.Bucket) b1).bucketOrd);
                double v2 = ((NumericMetricsAggregator.MultiValue) aggregator).metric(key, ((InternalTerms.Bucket) b2).bucketOrd);
                return Comparators.compareDiscardNaN(v1, v2, asc);
            };
        }

        return (b1, b2) -> {
            double v1 = ((NumericMetricsAggregator.SingleValue) aggregator).metric(((InternalTerms.Bucket) b1).bucketOrd);
            double v2 = ((NumericMetricsAggregator.SingleValue) aggregator).metric(((InternalTerms.Bucket) b2).bucketOrd);
            return Comparators.compareDiscardNaN(v1, v2, asc);
        };
    }

    @Override
    protected boolean shouldDefer(Aggregator aggregator) {
        return collectMode == SubAggCollectionMode.BREADTH_FIRST
                && !aggsUsedForSorting.contains(aggregator);
    }

}
