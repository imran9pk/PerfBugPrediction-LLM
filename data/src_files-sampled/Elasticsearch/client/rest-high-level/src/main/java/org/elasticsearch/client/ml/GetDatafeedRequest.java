package org.elasticsearch.client.ml;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.client.ml.datafeed.DatafeedConfig;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class GetDatafeedRequest extends ActionRequest implements ToXContentObject {

    public static final ParseField DATAFEED_IDS = new ParseField("datafeed_ids");
    public static final ParseField ALLOW_NO_DATAFEEDS = new ParseField("allow_no_datafeeds");

    private static final String ALL_DATAFEEDS = "_all";
    private final List<String> datafeedIds;
    private Boolean allowNoDatafeeds;

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<GetDatafeedRequest, Void> PARSER = new ConstructingObjectParser<>(
        "get_datafeed_request",
        true, a -> new GetDatafeedRequest(a[0] == null ? new ArrayList<>() : (List<String>) a[0]));

    static {
        PARSER.declareStringArray(ConstructingObjectParser.optionalConstructorArg(), DATAFEED_IDS);
        PARSER.declareBoolean(GetDatafeedRequest::setAllowNoDatafeeds, ALLOW_NO_DATAFEEDS);
    }

    public static GetDatafeedRequest getAllDatafeedsRequest() {
        return new GetDatafeedRequest(ALL_DATAFEEDS);
    }

    public GetDatafeedRequest(String... datafeedIds) {
        this(Arrays.asList(datafeedIds));
    }

    GetDatafeedRequest(List<String> datafeedIds) {
        if (datafeedIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("datafeedIds must not contain null values");
        }
        this.datafeedIds = new ArrayList<>(datafeedIds);
    }

    public List<String> getDatafeedIds() {
        return datafeedIds;
    }

    public void setAllowNoDatafeeds(boolean allowNoDatafeeds) {
        this.allowNoDatafeeds = allowNoDatafeeds;
    }

    public Boolean getAllowNoDatafeeds() {
        return allowNoDatafeeds;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(datafeedIds, allowNoDatafeeds);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || other.getClass() != getClass()) {
            return false;
        }

        GetDatafeedRequest that = (GetDatafeedRequest) other;
        return Objects.equals(datafeedIds, that.datafeedIds) &&
            Objects.equals(allowNoDatafeeds, that.allowNoDatafeeds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (datafeedIds.isEmpty() == false) {
            builder.field(DATAFEED_IDS.getPreferredName(), datafeedIds);
        }

        if (allowNoDatafeeds != null) {
            builder.field(ALLOW_NO_DATAFEEDS.getPreferredName(), allowNoDatafeeds);
        }

        builder.endObject();
        return builder;
    }
}
