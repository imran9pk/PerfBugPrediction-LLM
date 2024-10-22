package org.elasticsearch.action.search;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public final class SearchRequest extends ActionRequest implements IndicesRequest.Replaceable {
    private static final DeprecationLogger DEPRECATION_LOGGER = new DeprecationLogger(LogManager.getLogger(SearchRequest.class));

    private static final ToXContent.Params FORMAT_PARAMS = new ToXContent.MapParams(Collections.singletonMap("pretty", "false"));

    public static final int DEFAULT_PRE_FILTER_SHARD_SIZE = 128;
    public static final int DEFAULT_BATCHED_REDUCE_SIZE = 512;

    private static final long DEFAULT_ABSOLUTE_START_MILLIS = -1;

    private String localClusterAlias;
    private long absoluteStartMillis;
    private boolean finalReduce;

    private SearchType searchType = SearchType.DEFAULT;

    private String[] indices = Strings.EMPTY_ARRAY;

    @Nullable
    private String routing;
    @Nullable
    private String preference;

    private SearchSourceBuilder source;

    private Boolean requestCache;

    private Boolean allowPartialSearchResults;

    private Scroll scroll;

    private int batchedReduceSize = DEFAULT_BATCHED_REDUCE_SIZE;

    private int maxConcurrentShardRequests = 0;

    private int preFilterShardSize = DEFAULT_PRE_FILTER_SHARD_SIZE;

    private String[] types = Strings.EMPTY_ARRAY;

    public static final IndicesOptions DEFAULT_INDICES_OPTIONS = IndicesOptions.strictExpandOpenAndForbidClosedIgnoreThrottled();

    private IndicesOptions indicesOptions = DEFAULT_INDICES_OPTIONS;

    public SearchRequest() {
        this.localClusterAlias = null;
        this.absoluteStartMillis = DEFAULT_ABSOLUTE_START_MILLIS;
        this.finalReduce = true;
    }

    public SearchRequest(SearchRequest searchRequest) {
        this.allowPartialSearchResults = searchRequest.allowPartialSearchResults;
        this.batchedReduceSize = searchRequest.batchedReduceSize;
        this.indices = searchRequest.indices;
        this.indicesOptions = searchRequest.indicesOptions;
        this.maxConcurrentShardRequests = searchRequest.maxConcurrentShardRequests;
        this.preference = searchRequest.preference;
        this.preFilterShardSize = searchRequest.preFilterShardSize;
        this.requestCache = searchRequest.requestCache;
        this.routing = searchRequest.routing;
        this.scroll = searchRequest.scroll;
        this.searchType = searchRequest.searchType;
        this.source = searchRequest.source;
        this.types = searchRequest.types;
        this.localClusterAlias = searchRequest.localClusterAlias;
        this.absoluteStartMillis = searchRequest.absoluteStartMillis;
        this.finalReduce = searchRequest.finalReduce;
    }

    public SearchRequest(String... indices) {
        this(indices, new SearchSourceBuilder());
    }

    public SearchRequest(String[] indices, SearchSourceBuilder source) {
        this();
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        indices(indices);
        this.source = source;
    }

    SearchRequest(String localClusterAlias, long absoluteStartMillis, boolean finalReduce) {
        this.localClusterAlias = Objects.requireNonNull(localClusterAlias, "cluster alias must not be null");
        if (absoluteStartMillis < 0) {
            throw new IllegalArgumentException("absoluteStartMillis must not be negative but was [" + absoluteStartMillis + "]");
        }
        this.absoluteStartMillis = absoluteStartMillis;
        this.finalReduce = finalReduce;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        final Scroll scroll = scroll();
        if (source != null && source.trackTotalHits() == false && scroll != null) {
            validationException =
                addValidationError("disabling [track_total_hits] is not allowed in a scroll context", validationException);
        }
        if (source != null && source.from() > 0 && scroll != null) {
            validationException =
                addValidationError("using [from] is not allowed in a scroll context", validationException);
        }
        if (requestCache != null && requestCache && scroll != null) {
            DEPRECATION_LOGGER.deprecated("Explicitly set [request_cache] for a scroll query is deprecated and will return a 400 " +
                "error in future versions");
        }
        if (source != null && source.size() == 0 && scroll != null) {
            validationException = addValidationError("[size] cannot be [0] in a scroll context", validationException);
        }
        if (source != null && source.rescores() != null && source.rescores().isEmpty() == false && scroll != null) {
            DEPRECATION_LOGGER.deprecated("Using [rescore] for a scroll query is deprecated and will be ignored. From 7.0 on will " +
                    "return a 400 error");
        }
        return validationException;
    }

    @Nullable
    String getLocalClusterAlias() {
        return localClusterAlias;
    }

    boolean isFinalReduce() {
        return finalReduce;
    }

    long getOrCreateAbsoluteStartMillis() {
        return absoluteStartMillis == DEFAULT_ABSOLUTE_START_MILLIS ? System.currentTimeMillis() : absoluteStartMillis;
    }

    @Override
    public SearchRequest indices(String... indices) {
        Objects.requireNonNull(indices, "indices must not be null");
        for (String index : indices) {
            Objects.requireNonNull(index, "index must not be null");
        }
        this.indices = indices;
        return this;
    }

    @Override
    public IndicesOptions indicesOptions() {
        return indicesOptions;
    }

    public SearchRequest indicesOptions(IndicesOptions indicesOptions) {
        this.indicesOptions = Objects.requireNonNull(indicesOptions, "indicesOptions must not be null");
        return this;
    }

    public String[] types() {
        return types;
    }

    public SearchRequest types(String... types) {
        Objects.requireNonNull(types, "types must not be null");
        for (String type : types) {
            Objects.requireNonNull(type, "type must not be null");
        }
        this.types = types;
        return this;
    }

    public String routing() {
        return this.routing;
    }

    public SearchRequest routing(String routing) {
        this.routing = routing;
        return this;
    }

    public SearchRequest routing(String... routings) {
        this.routing = Strings.arrayToCommaDelimitedString(routings);
        return this;
    }

    public SearchRequest preference(String preference) {
        this.preference = preference;
        return this;
    }

    public String preference() {
        return this.preference;
    }

    public SearchRequest searchType(SearchType searchType) {
        this.searchType = Objects.requireNonNull(searchType, "searchType must not be null");
        return this;
    }

    public SearchRequest searchType(String searchType) {
        return searchType(SearchType.fromString(searchType));
    }

    public SearchRequest source(SearchSourceBuilder sourceBuilder) {
        this.source = Objects.requireNonNull(sourceBuilder, "source must not be null");
        return this;
    }

    public SearchSourceBuilder source() {
        return source;
    }

    public SearchType searchType() {
        return searchType;
    }

    @Override
    public String[] indices() {
        return indices;
    }

    public Scroll scroll() {
        return scroll;
    }

    public SearchRequest scroll(Scroll scroll) {
        this.scroll = scroll;
        return this;
    }

    public SearchRequest scroll(TimeValue keepAlive) {
        return scroll(new Scroll(keepAlive));
    }

    public SearchRequest scroll(String keepAlive) {
        return scroll(new Scroll(TimeValue.parseTimeValue(keepAlive, null, getClass().getSimpleName() + ".Scroll.keepAlive")));
    }

    public SearchRequest requestCache(Boolean requestCache) {
        this.requestCache = requestCache;
        return this;
    }

    public Boolean requestCache() {
        return this.requestCache;
    }
    
    public SearchRequest allowPartialSearchResults(boolean allowPartialSearchResults) {
        this.allowPartialSearchResults = allowPartialSearchResults;
        return this;
    }

    public Boolean allowPartialSearchResults() {
        return this.allowPartialSearchResults;
    }

    public void setBatchedReduceSize(int batchedReduceSize) {
        if (batchedReduceSize <= 1) {
            throw new IllegalArgumentException("batchedReduceSize must be >= 2");
        }
        this.batchedReduceSize = batchedReduceSize;
    }

    public int getBatchedReduceSize() {
        return batchedReduceSize;
    }

    public int getMaxConcurrentShardRequests() {
        return maxConcurrentShardRequests == 0 ? 256 : maxConcurrentShardRequests;
    }

    public void setMaxConcurrentShardRequests(int maxConcurrentShardRequests) {
        if (maxConcurrentShardRequests < 1) {
            throw new IllegalArgumentException("maxConcurrentShardRequests must be >= 1");
        }
        this.maxConcurrentShardRequests = maxConcurrentShardRequests;
    }
    public void setPreFilterShardSize(int preFilterShardSize) {
        if (preFilterShardSize < 1) {
            throw new IllegalArgumentException("preFilterShardSize must be >= 1");
        }
        this.preFilterShardSize = preFilterShardSize;
    }

    public int getPreFilterShardSize() {
        return preFilterShardSize;
    }

    boolean isMaxConcurrentShardRequestsSet() {
        return maxConcurrentShardRequests != 0;
    }

    public boolean isSuggestOnly() {
        return source != null && source.isSuggestOnly();
    }

    @Override
    public Task createTask(long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
        return new SearchTask(id, type, action, null, parentTaskId, headers) {
            @Override
            public String getDescription() {
                StringBuilder sb = new StringBuilder();
                sb.append("indices[");
                Strings.arrayToDelimitedString(indices, ",", sb);
                sb.append("], ");
                sb.append("types[");
                Strings.arrayToDelimitedString(types, ",", sb);
                sb.append("], ");
                sb.append("search_type[").append(searchType).append("], ");
                if (source != null) {

                    sb.append("source[").append(source.toString(FORMAT_PARAMS)).append("]");
                } else {
                    sb.append("source[]");
                }
                return sb.toString();
            }
        };
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        searchType = SearchType.fromId(in.readByte());
        indices = new String[in.readVInt()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = in.readString();
        }
        routing = in.readOptionalString();
        preference = in.readOptionalString();
        scroll = in.readOptionalWriteable(Scroll::new);
        source = in.readOptionalWriteable(SearchSourceBuilder::new);
        types = in.readStringArray();
        indicesOptions = IndicesOptions.readIndicesOptions(in);
        requestCache = in.readOptionalBoolean();
        batchedReduceSize = in.readVInt();
        if (in.getVersion().onOrAfter(Version.V_5_6_0)) {
            maxConcurrentShardRequests = in.readVInt();
            preFilterShardSize = in.readVInt();
        }
        if (in.getVersion().onOrAfter(Version.V_6_3_0)) {
            allowPartialSearchResults = in.readOptionalBoolean();
        }
        if (in.getVersion().onOrAfter(Version.V_6_7_0)) {
            localClusterAlias = in.readOptionalString();
            if (localClusterAlias != null) {
                absoluteStartMillis = in.readVLong();
                finalReduce = in.readBoolean();
            } else {
                absoluteStartMillis = DEFAULT_ABSOLUTE_START_MILLIS;
                finalReduce = true;
            }
        } else {
            localClusterAlias = null;
            absoluteStartMillis = DEFAULT_ABSOLUTE_START_MILLIS;
            finalReduce = true;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeByte(searchType.id());
        out.writeVInt(indices.length);
        for (String index : indices) {
            out.writeString(index);
        }
        out.writeOptionalString(routing);
        out.writeOptionalString(preference);
        out.writeOptionalWriteable(scroll);
        out.writeOptionalWriteable(source);
        out.writeStringArray(types);
        indicesOptions.writeIndicesOptions(out);
        out.writeOptionalBoolean(requestCache);
        out.writeVInt(batchedReduceSize);
        if (out.getVersion().onOrAfter(Version.V_5_6_0)) {
            out.writeVInt(maxConcurrentShardRequests);
            out.writeVInt(preFilterShardSize);
        }
        if (out.getVersion().onOrAfter(Version.V_6_3_0)) {
            out.writeOptionalBoolean(allowPartialSearchResults);
        }
        if (out.getVersion().onOrAfter(Version.V_6_7_0)) {
            out.writeOptionalString(localClusterAlias);
            if (localClusterAlias != null) {
                out.writeVLong(absoluteStartMillis);
                out.writeBoolean(finalReduce);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SearchRequest that = (SearchRequest) o;
        return searchType == that.searchType &&
                Arrays.equals(indices, that.indices) &&
                Objects.equals(routing, that.routing) &&
                Objects.equals(preference, that.preference) &&
                Objects.equals(source, that.source) &&
                Objects.equals(requestCache, that.requestCache)  &&
                Objects.equals(scroll, that.scroll) &&
                Arrays.equals(types, that.types) &&
                Objects.equals(batchedReduceSize, that.batchedReduceSize) &&
                Objects.equals(maxConcurrentShardRequests, that.maxConcurrentShardRequests) &&
                Objects.equals(preFilterShardSize, that.preFilterShardSize) &&
                Objects.equals(indicesOptions, that.indicesOptions) &&
                Objects.equals(allowPartialSearchResults, that.allowPartialSearchResults) &&
                Objects.equals(localClusterAlias, that.localClusterAlias) &&
                absoluteStartMillis == that.absoluteStartMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchType, Arrays.hashCode(indices), routing, preference, source, requestCache,
                scroll, Arrays.hashCode(types), indicesOptions, batchedReduceSize, maxConcurrentShardRequests, preFilterShardSize,
                allowPartialSearchResults, localClusterAlias, absoluteStartMillis);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "searchType=" + searchType +
                ", indices=" + Arrays.toString(indices) +
                ", indicesOptions=" + indicesOptions +
                ", types=" + Arrays.toString(types) +
                ", routing='" + routing + '\'' +
                ", preference='" + preference + '\'' +
                ", requestCache=" + requestCache +
                ", scroll=" + scroll +
                ", maxConcurrentShardRequests=" + maxConcurrentShardRequests +
                ", batchedReduceSize=" + batchedReduceSize +
                ", preFilterShardSize=" + preFilterShardSize +
                ", allowPartialSearchResults=" + allowPartialSearchResults +
                ", localClusterAlias=" + localClusterAlias +
                ", getOrCreateAbsoluteStartMillis=" + absoluteStartMillis +
                ", source=" + source + '}';
    }
}
