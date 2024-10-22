package com.palantir.atlasdb.keyvalue.cassandra.thrift;

import com.palantir.atlasdb.keyvalue.api.TimestampRangeDelete;
import java.util.function.ToLongFunction;
import org.apache.cassandra.thrift.Deletion;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.SlicePredicate;

public final class Mutations {

    private Mutations() {}

    public static Mutation fromTimestampRangeDelete(
            byte[] columnName,
            TimestampRangeDelete delete,
            long rangeTombstoneCassandraTimestamp,
            ToLongFunction<TimestampRangeDelete> exclusiveTimestampExtractor) {
        Deletion deletion = new Deletion()
                .setTimestamp(rangeTombstoneCassandraTimestamp)
                .setPredicate(getSlicePredicate(columnName, delete, exclusiveTimestampExtractor.applyAsLong(delete)));

        return new Mutation().setDeletion(deletion);
    }

    private static SlicePredicate getSlicePredicate(
            byte[] columnName, TimestampRangeDelete delete, long maxExclusiveTimestamp) {
        return delete.deleteSentinels()
                ? SlicePredicates.rangeTombstoneIncludingSentinelForColumn(columnName, maxExclusiveTimestamp)
                : SlicePredicates.rangeTombstoneForColumn(columnName, maxExclusiveTimestamp);
    }
}
