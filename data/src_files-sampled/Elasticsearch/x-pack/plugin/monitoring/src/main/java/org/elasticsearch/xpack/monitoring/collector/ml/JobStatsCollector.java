package org.elasticsearch.xpack.monitoring.collector.ml;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.xpack.core.XPackClient;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.ml.action.GetJobsStatsAction;
import org.elasticsearch.xpack.core.ml.client.MachineLearningClient;
import org.elasticsearch.xpack.core.monitoring.exporter.MonitoringDoc;
import org.elasticsearch.xpack.monitoring.collector.Collector;

import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.xpack.core.ClientHelper.MONITORING_ORIGIN;

public class JobStatsCollector extends Collector {

    public static final Setting<TimeValue> JOB_STATS_TIMEOUT = collectionTimeoutSetting("ml.job.stats.timeout");

    private final Settings settings;
    private final ThreadContext threadContext;
    private final MachineLearningClient client;

    public JobStatsCollector(final Settings settings, final ClusterService clusterService,
                             final XPackLicenseState licenseState, final Client client) {
        this(settings, clusterService, licenseState, new XPackClient(client).machineLearning(), client.threadPool().getThreadContext());
    }

    JobStatsCollector(final Settings settings, final ClusterService clusterService,
                      final XPackLicenseState licenseState, final MachineLearningClient client, final ThreadContext threadContext) {
        super(JobStatsMonitoringDoc.TYPE, clusterService, JOB_STATS_TIMEOUT, licenseState);
        this.settings = settings;
        this.client = client;
        this.threadContext = threadContext;
    }

    @Override
    protected boolean shouldCollect(final boolean isElectedMaster) {
        return isElectedMaster
                && super.shouldCollect(isElectedMaster)
                && XPackSettings.MACHINE_LEARNING_ENABLED.get(settings)
                && licenseState.isMachineLearningAllowed();
    }

    @Override
    protected List<MonitoringDoc> doCollect(final MonitoringDoc.Node node,
                                            final long interval,
                                            final ClusterState clusterState) throws Exception {
        try (ThreadContext.StoredContext ignore = threadContext.stashWithOrigin(MONITORING_ORIGIN)) {
            final GetJobsStatsAction.Response jobs =
                    client.getJobsStats(new GetJobsStatsAction.Request(MetaData.ALL))
                            .actionGet(getCollectionTimeout());

            final long timestamp = timestamp();
            final String clusterUuid = clusterUuid(clusterState);

            return jobs.getResponse().results().stream()
                    .map(jobStats -> new JobStatsMonitoringDoc(clusterUuid, timestamp, interval, node, jobStats))
                    .collect(Collectors.toList());
        }
    }

}
