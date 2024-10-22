package org.elasticsearch.xpack.ml.job.persistence;

import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.xpack.core.ml.job.persistence.AnomalyDetectorsIndex;
import org.elasticsearch.xpack.core.ml.job.results.Result;

public abstract class BatchedResultsIterator<T> extends BatchedDocumentsIterator<Result<T>> {

    private final ResultsFilterBuilder filterBuilder;

    public BatchedResultsIterator(Client client, String jobId, String resultType) {
        super(client, AnomalyDetectorsIndex.jobResultsAliasedName(jobId));
        this.filterBuilder = new ResultsFilterBuilder(new TermsQueryBuilder(Result.RESULT_TYPE.getPreferredName(), resultType));
    }

    public BatchedResultsIterator<T> timeRange(long startEpochMs, long endEpochMs) {
        filterBuilder.timeRange(Result.TIMESTAMP.getPreferredName(), startEpochMs, endEpochMs);
        return this;
    }

    public BatchedResultsIterator<T> includeInterim(boolean includeInterim) {
        filterBuilder.interim(includeInterim);
        return this;
    }

    protected final QueryBuilder getQuery() {
        return filterBuilder.build();
    }
}
