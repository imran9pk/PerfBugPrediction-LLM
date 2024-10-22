package org.elasticsearch.xpack.ml.datafeed.extractor.scroll;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.ClearScrollAction;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.StoredFieldsContext;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ml.datafeed.extractor.DataExtractor;
import org.elasticsearch.xpack.core.ml.datafeed.extractor.ExtractorUtils;
import org.elasticsearch.xpack.ml.datafeed.extractor.fields.ExtractedField;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

class ScrollDataExtractor implements DataExtractor {

    private static final Logger LOGGER = LogManager.getLogger(ScrollDataExtractor.class);
    private static final TimeValue SCROLL_TIMEOUT = new TimeValue(30, TimeUnit.MINUTES);

    private final Client client;
    private final ScrollDataExtractorContext context;
    private String scrollId;
    private boolean isCancelled;
    private boolean hasNext;
    private Long timestampOnCancel;
    protected Long lastTimestamp;
    private boolean searchHasShardFailure;

    ScrollDataExtractor(Client client, ScrollDataExtractorContext dataExtractorContext) {
        this.client = Objects.requireNonNull(client);
        context = Objects.requireNonNull(dataExtractorContext);
        hasNext = true;
        searchHasShardFailure = false;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void cancel() {
        LOGGER.trace("[{}] Data extractor received cancel request", context.jobId);
        isCancelled = true;
    }

    @Override
    public long getEndTime() {
        return context.end;
    }

    @Override
    public Optional<InputStream> next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Optional<InputStream> stream = tryNextStream();
        if (!stream.isPresent()) {
            hasNext = false;
        }
        return stream;
    }

    private Optional<InputStream> tryNextStream() throws IOException {
        try {
            return scrollId == null ?
                Optional.ofNullable(initScroll(context.start)) : Optional.ofNullable(continueScroll());
        } catch (Exception e) {
            clearScroll();
            throw e;
        }
    }

    protected InputStream initScroll(long startTimestamp) throws IOException {
        LOGGER.debug("[{}] Initializing scroll", context.jobId);
        SearchResponse searchResponse = executeSearchRequest(buildSearchRequest(startTimestamp));
        LOGGER.debug("[{}] Search response was obtained", context.jobId);
        return processSearchResponse(searchResponse);
    }

    protected SearchResponse executeSearchRequest(SearchRequestBuilder searchRequestBuilder) {
        return ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client, searchRequestBuilder::get);
    }

    private SearchRequestBuilder buildSearchRequest(long start) {
        SearchRequestBuilder searchRequestBuilder = SearchAction.INSTANCE.newRequestBuilder(client)
                .setScroll(SCROLL_TIMEOUT)
                .addSort(context.extractedFields.timeField(), SortOrder.ASC)
                .setIndices(context.indices)
                .setTypes(context.types)
                .setSize(context.scrollSize)
                .setQuery(ExtractorUtils.wrapInTimeRangeQuery(
                        context.query, context.extractedFields.timeField(), start, context.end));

        for (ExtractedField docValueField : context.extractedFields.getDocValueFields()) {
            searchRequestBuilder.addDocValueField(docValueField.getName(), docValueField.getDocValueFormat());
        }
        String[] sourceFields = context.extractedFields.getSourceFields();
        if (sourceFields.length == 0) {
            searchRequestBuilder.setFetchSource(false);
            searchRequestBuilder.storedFields(StoredFieldsContext._NONE_);
        } else {
            searchRequestBuilder.setFetchSource(sourceFields, null);
        }
        context.scriptFields.forEach(f -> searchRequestBuilder.addScriptField(f.fieldName(), f.script()));
        return searchRequestBuilder;
    }

    private InputStream processSearchResponse(SearchResponse searchResponse) throws IOException {

        scrollId = searchResponse.getScrollId();

        if (searchResponse.getFailedShards() > 0 && searchHasShardFailure == false) {
            LOGGER.debug("[{}] Resetting scroll search after shard failure", context.jobId);
            markScrollAsErrored();
            return initScroll(lastTimestamp == null ? context.start : lastTimestamp);
        }

        ExtractorUtils.checkSearchWasSuccessful(context.jobId, searchResponse);
        if (searchResponse.getHits().getHits().length == 0) {
            hasNext = false;
            clearScroll();
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (SearchHitToJsonProcessor hitProcessor = new SearchHitToJsonProcessor(context.extractedFields, outputStream)) {
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                if (isCancelled) {
                    Long timestamp = context.extractedFields.timeFieldValue(hit);
                    if (timestamp != null) {
                        if (timestampOnCancel == null) {
                            timestampOnCancel = timestamp;
                        } else if (timestamp.equals(timestampOnCancel) == false) {
                            hasNext = false;
                            clearScroll();
                            break;
                        }
                    }
                }
                hitProcessor.process(hit);
            }
            SearchHit lastHit = searchResponse.getHits().getHits()[searchResponse.getHits().getHits().length -1];
            lastTimestamp = context.extractedFields.timeFieldValue(lastHit);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private InputStream continueScroll() throws IOException {
        LOGGER.debug("[{}] Continuing scroll with id [{}]", context.jobId, scrollId);
        SearchResponse searchResponse;
        try {
             searchResponse = executeSearchScrollRequest(scrollId);
        } catch (SearchPhaseExecutionException searchExecutionException) {
            if (searchHasShardFailure == false) {
                LOGGER.debug("[{}] Reinitializing scroll due to SearchPhaseExecutionException", context.jobId);
                markScrollAsErrored();
                searchResponse = executeSearchRequest(buildSearchRequest(lastTimestamp == null ? context.start : lastTimestamp));
            } else {
                throw searchExecutionException;
            }
        }
        LOGGER.debug("[{}] Search response was obtained", context.jobId);
        return processSearchResponse(searchResponse);
    }

    private void markScrollAsErrored() {
        clearScroll();
        if (lastTimestamp != null) {
            lastTimestamp++;
        }
        searchHasShardFailure = true;
    }

    protected SearchResponse executeSearchScrollRequest(String scrollId) {
        return ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client,
                () -> SearchScrollAction.INSTANCE.newRequestBuilder(client)
                .setScroll(SCROLL_TIMEOUT)
                .setScrollId(scrollId)
                .get());
    }

    private void clearScroll() {
        if (scrollId != null) {
            ClearScrollRequest request = new ClearScrollRequest();
            request.addScrollId(scrollId);
            ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client,
                    () -> client.execute(ClearScrollAction.INSTANCE, request).actionGet());
            scrollId = null;
        }
    }
}