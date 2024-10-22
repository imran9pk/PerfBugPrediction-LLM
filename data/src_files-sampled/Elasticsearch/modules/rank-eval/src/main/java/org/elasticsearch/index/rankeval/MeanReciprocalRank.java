package org.elasticsearch.index.rankeval;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.optionalConstructorArg;
import static org.elasticsearch.index.rankeval.EvaluationMetric.joinHitsWithRatings;

public class MeanReciprocalRank implements EvaluationMetric {

    public static final String NAME = "mean_reciprocal_rank";

    private static final int DEFAULT_RATING_THRESHOLD = 1;
    private static final int DEFAULT_K = 10;

    private final int k;

    private final int relevantRatingThreshhold;

    public MeanReciprocalRank() {
        this(DEFAULT_RATING_THRESHOLD, DEFAULT_K);
    }

    MeanReciprocalRank(StreamInput in) throws IOException {
        this.relevantRatingThreshhold = in.readVInt();
        this.k = in.readVInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(this.relevantRatingThreshhold);
        out.writeVInt(this.k);
    }

    public MeanReciprocalRank(int relevantRatingThreshold, int k) {
        if (relevantRatingThreshold < 0) {
            throw new IllegalArgumentException("Relevant rating threshold for precision must be positive integer.");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("Window size k must be positive.");
        }
        this.k = k;
        this.relevantRatingThreshhold = relevantRatingThreshold;
    }

    int getK() {
        return this.k;
    }

    @Override
    public Optional<Integer> forcedSearchSize() {
        return Optional.of(k);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public int getRelevantRatingThreshold() {
        return relevantRatingThreshhold;
    }

    @Override
    public EvalQueryQuality evaluate(String taskId, SearchHit[] hits, List<RatedDocument> ratedDocs) {
        List<RatedSearchHit> ratedHits = joinHitsWithRatings(hits, ratedDocs);
        int firstRelevant = -1;
        int rank = 1;
        for (RatedSearchHit hit : ratedHits) {
            Optional<Integer> rating = hit.getRating();
            if (rating.isPresent()) {
                if (rating.get() >= this.relevantRatingThreshhold) {
                    firstRelevant = rank;
                    break;
                }
            }
            rank++;
        }

        double reciprocalRank = (firstRelevant == -1) ? 0 : 1.0d / firstRelevant;
        EvalQueryQuality evalQueryQuality = new EvalQueryQuality(taskId, reciprocalRank);
        evalQueryQuality.setMetricDetails(new Detail(firstRelevant));
        evalQueryQuality.addHitsAndRatings(ratedHits);
        return evalQueryQuality;
    }

    private static final ParseField RELEVANT_RATING_FIELD = new ParseField("relevant_rating_threshold");
    private static final ParseField K_FIELD = new ParseField("k");
    private static final ConstructingObjectParser<MeanReciprocalRank, Void> PARSER = new ConstructingObjectParser<>("reciprocal_rank",
            args -> {
                Integer optionalThreshold = (Integer) args[0];
                Integer optionalK = (Integer) args[1];
                return new MeanReciprocalRank(optionalThreshold == null ? DEFAULT_RATING_THRESHOLD : optionalThreshold,
                        optionalK == null ? DEFAULT_K : optionalK);
            });

    static {
        PARSER.declareInt(optionalConstructorArg(), RELEVANT_RATING_FIELD);
        PARSER.declareInt(optionalConstructorArg(), K_FIELD);
    }

    public static MeanReciprocalRank fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startObject(NAME);
        builder.field(RELEVANT_RATING_FIELD.getPreferredName(), this.relevantRatingThreshhold);
        builder.field(K_FIELD.getPreferredName(), this.k);
        builder.endObject();
        builder.endObject();
        return builder;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MeanReciprocalRank other = (MeanReciprocalRank) obj;
        return Objects.equals(relevantRatingThreshhold, other.relevantRatingThreshhold)
                && Objects.equals(k, other.k);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(relevantRatingThreshhold, k);
    }

    public static final class Detail implements MetricDetail {

        private final int firstRelevantRank;
        private static ParseField FIRST_RELEVANT_RANK_FIELD = new ParseField("first_relevant");

        Detail(int firstRelevantRank) {
            this.firstRelevantRank = firstRelevantRank;
        }

        Detail(StreamInput in) throws IOException {
            this.firstRelevantRank = in.readVInt();
        }

        @Override
        public
        String getMetricName() {
            return NAME;
        }

        @Override
        public XContentBuilder innerToXContent(XContentBuilder builder, Params params)
                throws IOException {
            return builder.field(FIRST_RELEVANT_RANK_FIELD.getPreferredName(), firstRelevantRank);
        }

        private static final ConstructingObjectParser<Detail, Void> PARSER = new ConstructingObjectParser<>(NAME, true, args -> {
            return new Detail((Integer) args[0]);
        });

        static {
            PARSER.declareInt(constructorArg(), FIRST_RELEVANT_RANK_FIELD);
        }

        public static Detail fromXContent(XContentParser parser) {
            return PARSER.apply(parser, null);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(firstRelevantRank);
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        public int getFirstRelevantRank() {
            return firstRelevantRank;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MeanReciprocalRank.Detail other = (MeanReciprocalRank.Detail) obj;
            return Objects.equals(firstRelevantRank, other.firstRelevantRank);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstRelevantRank);
        }
    }
}
