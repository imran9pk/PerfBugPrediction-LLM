package org.elasticsearch.plugin.noop.action.search;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.internal.InternalSearchResponse;
import org.elasticsearch.search.profile.SearchProfileShardResults;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Collections;

public class TransportNoopSearchAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    @Inject
    public TransportNoopSearchAction(Settings settings, ThreadPool threadPool, TransportService transportService, ActionFilters
        actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, NoopSearchAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
            SearchRequest::new);
    }

    @Override
    protected void doExecute(SearchRequest request, ActionListener<SearchResponse> listener) {
        listener.onResponse(new SearchResponse(new InternalSearchResponse(
            new SearchHits(
                new SearchHit[0], 0L, 0.0f),
            new InternalAggregations(Collections.emptyList()),
            new Suggest(Collections.emptyList()),
            new SearchProfileShardResults(Collections.emptyMap()), false, false, 1), "", 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY));
    }
}
