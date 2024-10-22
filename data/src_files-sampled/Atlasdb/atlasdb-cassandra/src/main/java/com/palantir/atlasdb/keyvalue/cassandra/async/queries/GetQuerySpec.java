package com.palantir.atlasdb.keyvalue.cassandra.async.queries;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.Value;
import com.palantir.atlasdb.keyvalue.impl.AbstractKeyValueService;
import com.palantir.logsafe.Preconditions;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("InlineFormatString") public final class GetQuerySpec implements CqlQuerySpec<Optional<Value>> {

    private static final String QUERY_FORMAT = "SELECT value, column2 FROM \"%s\".\"%s\" "
            + "WHERE key = :row AND column1 = :column AND column2 > :timestamp "
            + "LIMIT 1;";

    private final CqlQueryContext cqlQueryContext;
    private final GetQueryParameters getQueryParameters;
    private final GetQueryAccumulator getQueryAccumulator = new GetQueryAccumulator();

    public GetQuerySpec(CqlQueryContext cqlQueryContext, GetQueryParameters getQueryParameters) {
        this.cqlQueryContext = cqlQueryContext;
        this.getQueryParameters = getQueryParameters;
    }

    @Override
    public CqlQueryContext cqlQueryContext() {
        return cqlQueryContext;
    }

    @Override
    public String formatQueryString() {
        return String.format(
                QUERY_FORMAT,
                cqlQueryContext().keyspace(),
                AbstractKeyValueService.internalTableName(cqlQueryContext().tableReference()));
    }

    @Override
    public QueryType queryType() {
        return QueryType.GET;
    }

    @Override
    public Statement makeExecutableStatement(PreparedStatement preparedStatement) {
        return preparedStatement
                .bind()
                .setBytes("row", toReadOnlyByteBuffer(getQueryParameters.cell().getRowName()))
                .setBytes(
                        "column", toReadOnlyByteBuffer(getQueryParameters.cell().getColumnName()))
                .setLong("timestamp", getQueryParameters.queryTimestamp());
    }

    private static ByteBuffer toReadOnlyByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    @Override
    public ConsistencyLevel queryConsistency() {
        return ConsistencyLevel.LOCAL_QUORUM;
    }

    @Override
    public RowStreamAccumulator<Optional<Value>> rowStreamAccumulator() {
        return getQueryAccumulator;
    }

    @org.immutables.value.Value.Immutable
    public interface GetQueryParameters {
        Cell cell();

        long humanReadableTimestamp();

        default long queryTimestamp() {
            return ~humanReadableTimestamp();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GetQuerySpec that = (GetQuerySpec) other;
        return cqlQueryContext.equals(that.cqlQueryContext) && getQueryParameters.equals(that.getQueryParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cqlQueryContext, getQueryParameters);
    }

    private static final class GetQueryAccumulator implements RowStreamAccumulator<Optional<Value>> {

        private volatile Value resultValue = null;
        private volatile boolean assigned = false;

        @Override
        public void accumulateRowStream(Stream<Row> rowStream) {
            Preconditions.checkState(
                    !assigned, "Multiple calls to accumulateRowStream, wrong usage of this implementation");
            assigned = true;
            resultValue =
                    rowStream.findFirst().map(GetQueryAccumulator::parseValue).orElse(null);
        }

        @Override
        public Optional<Value> result() {
            Preconditions.checkState(assigned, "Result has never been assigned(query associated never ran).");
            return Optional.ofNullable(resultValue);
        }

        private static Value parseValue(Row row) {
            return Value.create(row.getBytes(0).array(), ~row.getLong(1));
        }
    }
}
