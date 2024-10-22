package org.elasticsearch.action.admin.cluster.settings;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.AckedClusterStateUpdateTask;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.allocation.AllocationService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

public class TransportClusterUpdateSettingsAction extends
    TransportMasterNodeAction<ClusterUpdateSettingsRequest, ClusterUpdateSettingsResponse> {

    private final AllocationService allocationService;

    private final ClusterSettings clusterSettings;

    @Inject
    public TransportClusterUpdateSettingsAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                                ThreadPool threadPool, AllocationService allocationService, ActionFilters actionFilters,
                                                IndexNameExpressionResolver indexNameExpressionResolver, ClusterSettings clusterSettings) {
        super(settings, ClusterUpdateSettingsAction.NAME, false, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, ClusterUpdateSettingsRequest::new);
        this.allocationService = allocationService;
        this.clusterSettings = clusterSettings;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(ClusterUpdateSettingsRequest request, ClusterState state) {
        if (request.transientSettings().size() + request.persistentSettings().size() == 1) {
            if (MetaData.SETTING_READ_ONLY_SETTING.exists(request.persistentSettings())
                || MetaData.SETTING_READ_ONLY_SETTING.exists(request.transientSettings())
                || MetaData.SETTING_READ_ONLY_ALLOW_DELETE_SETTING.exists(request.transientSettings())
                || MetaData.SETTING_READ_ONLY_ALLOW_DELETE_SETTING.exists(request.persistentSettings())) {
                return null;
            }
        }
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }


    @Override
    protected ClusterUpdateSettingsResponse newResponse() {
        return new ClusterUpdateSettingsResponse();
    }

    @Override
    protected void masterOperation(final ClusterUpdateSettingsRequest request, final ClusterState state,
                                   final ActionListener<ClusterUpdateSettingsResponse> listener) {
        final SettingsUpdater updater = new SettingsUpdater(clusterSettings);
        clusterService.submitStateUpdateTask("cluster_update_settings",
                new AckedClusterStateUpdateTask<ClusterUpdateSettingsResponse>(Priority.IMMEDIATE, request, listener) {

            private volatile boolean changed = false;

            @Override
            protected ClusterUpdateSettingsResponse newResponse(boolean acknowledged) {
                return new ClusterUpdateSettingsResponse(acknowledged, updater.getTransientUpdates(), updater.getPersistentUpdate());
            }

            @Override
            public void onAllNodesAcked(@Nullable Exception e) {
                if (changed) {
                    reroute(true);
                } else {
                    super.onAllNodesAcked(e);
                }
            }

            @Override
            public void onAckTimeout() {
                if (changed) {
                    reroute(false);
                } else {
                    super.onAckTimeout();
                }
            }

            private void reroute(final boolean updateSettingsAcked) {
                if (!clusterService.state().nodes().isLocalNodeElectedMaster()) {
                    logger.debug("Skipping reroute after cluster update settings, because node is no longer master");
                    listener.onResponse(new ClusterUpdateSettingsResponse(updateSettingsAcked, updater.getTransientUpdates(),
                        updater.getPersistentUpdate()));
                    return;
                }

                clusterService.submitStateUpdateTask("reroute_after_cluster_update_settings",
                        new AckedClusterStateUpdateTask<ClusterUpdateSettingsResponse>(Priority.URGENT, request, listener) {

                    @Override
                    public boolean mustAck(DiscoveryNode discoveryNode) {
                        return updateSettingsAcked;
                    }

                    @Override
                    protected ClusterUpdateSettingsResponse newResponse(boolean acknowledged) {
                        return new ClusterUpdateSettingsResponse(updateSettingsAcked && acknowledged, updater.getTransientUpdates(),
                            updater.getPersistentUpdate());
                    }

                    @Override
                    public void onNoLongerMaster(String source) {
                        logger.debug("failed to preform reroute after cluster settings were updated - current node is no longer a master");
                        listener.onResponse(new ClusterUpdateSettingsResponse(updateSettingsAcked, updater.getTransientUpdates(),
                            updater.getPersistentUpdate()));
                    }

                    @Override
                    public void onFailure(String source, Exception e) {
                        logger.debug(() -> new ParameterizedMessage("failed to perform [{}]", source), e);
                        listener.onFailure(new ElasticsearchException("reroute after update settings failed", e));
                    }

                    @Override
                    public ClusterState execute(final ClusterState currentState) {
                        return allocationService.reroute(currentState, "reroute after cluster update settings");
                    }
                });
            }

            @Override
            public void onFailure(String source, Exception e) {
                logger.debug(() -> new ParameterizedMessage("failed to perform [{}]", source), e);
                super.onFailure(source, e);
            }

            @Override
            public ClusterState execute(final ClusterState currentState) {
                final ClusterState clusterState =
                        updater.updateSettings(
                                currentState,
                                clusterSettings.upgradeSettings(request.transientSettings()),
                                clusterSettings.upgradeSettings(request.persistentSettings()),
                                logger);
                changed = clusterState != currentState;
                return clusterState;
            }
        });
    }

}
