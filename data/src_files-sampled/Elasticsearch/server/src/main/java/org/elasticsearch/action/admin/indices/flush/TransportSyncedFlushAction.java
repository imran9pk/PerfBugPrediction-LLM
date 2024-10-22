package org.elasticsearch.action.admin.indices.flush;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.flush.SyncedFlushService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

public class TransportSyncedFlushAction extends HandledTransportAction<SyncedFlushRequest, SyncedFlushResponse> {

    SyncedFlushService syncedFlushService;

    @Inject
    public TransportSyncedFlushAction(Settings settings, ThreadPool threadPool,
                                      TransportService transportService, ActionFilters actionFilters,
                                      IndexNameExpressionResolver indexNameExpressionResolver,
                                      SyncedFlushService syncedFlushService) {
        super(settings, SyncedFlushAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
            SyncedFlushRequest::new);
        this.syncedFlushService = syncedFlushService;
    }

    @Override
    protected void doExecute(SyncedFlushRequest request, ActionListener<SyncedFlushResponse> listener) {
        syncedFlushService.attemptSyncedFlush(request.indices(), request.indicesOptions(), listener);
    }
}
