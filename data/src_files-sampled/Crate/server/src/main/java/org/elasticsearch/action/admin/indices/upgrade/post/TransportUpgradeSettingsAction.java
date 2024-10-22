package org.elasticsearch.action.admin.indices.upgrade.post;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ack.ClusterStateUpdateResponse;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetadataUpdateSettingsService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

public class TransportUpgradeSettingsAction extends TransportMasterNodeAction<UpgradeSettingsRequest, AcknowledgedResponse> {

    private final MetadataUpdateSettingsService updateSettingsService;

    @Inject
    public TransportUpgradeSettingsAction(TransportService transportService,
                                          ClusterService clusterService,
                                          ThreadPool threadPool,
                                          MetadataUpdateSettingsService updateSettingsService,
                                          IndexNameExpressionResolver indexNameExpressionResolver) {
        super(
            UpgradeSettingsAction.NAME,
            transportService,
            clusterService,
            threadPool,
            UpgradeSettingsRequest::new,
            indexNameExpressionResolver
        );
        this.updateSettingsService = updateSettingsService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(UpgradeSettingsRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected AcknowledgedResponse read(StreamInput in) throws IOException {
        return new AcknowledgedResponse(in);
    }

    @Override
    protected void masterOperation(Task task,
                                   final UpgradeSettingsRequest request,
                                   final ClusterState state,
                                   final ActionListener<AcknowledgedResponse> listener) {
        UpgradeSettingsClusterStateUpdateRequest clusterStateUpdateRequest = new UpgradeSettingsClusterStateUpdateRequest()
                .ackTimeout(request.timeout())
                .versions(request.versions())
                .masterNodeTimeout(request.masterNodeTimeout());

        updateSettingsService.upgradeIndexSettings(clusterStateUpdateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new AcknowledgedResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Exception t) {
                logger.debug(() -> new ParameterizedMessage("failed to upgrade minimum compatibility version settings on indices [{}]", request.versions().keySet()), t);
                listener.onFailure(t);
            }
        });
    }
}
