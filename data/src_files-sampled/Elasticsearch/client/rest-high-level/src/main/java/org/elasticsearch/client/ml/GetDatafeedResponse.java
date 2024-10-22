package org.elasticsearch.client.ml;

import org.elasticsearch.client.ml.datafeed.DatafeedConfig;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;

public class GetDatafeedResponse extends AbstractResultResponse<DatafeedConfig> {

    public static final ParseField RESULTS_FIELD = new ParseField("datafeeds");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<GetDatafeedResponse, Void> PARSER =
        new ConstructingObjectParser<>("get_datafeed_response", true,
            a -> new GetDatafeedResponse((List<DatafeedConfig.Builder>) a[0], (long) a[1]));

    static {
        PARSER.declareObjectArray(constructorArg(), DatafeedConfig.PARSER, RESULTS_FIELD);
        PARSER.declareLong(constructorArg(), AbstractResultResponse.COUNT);
    }

    GetDatafeedResponse(List<DatafeedConfig.Builder> datafeedBuilders, long count) {
        super(RESULTS_FIELD, datafeedBuilders.stream().map(DatafeedConfig.Builder::build).collect(Collectors.toList()), count);
    }

    public List<DatafeedConfig> datafeeds() {
        return results;
    }

    public static GetDatafeedResponse fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results, count);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        GetDatafeedResponse other = (GetDatafeedResponse) obj;
        return Objects.equals(results, other.results) && count == other.count;
    }

    @Override
    public final String toString() {
        return Strings.toString(this);
    }
}
