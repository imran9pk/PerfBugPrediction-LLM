package com.palantir.atlasdb.table.api;

import com.palantir.atlasdb.keyvalue.api.ColumnSelection;
import java.util.List;

public interface AtlasDbNamedImmutableTable<ROW, COLUMN_VALUE, ROW_RESULT>
        extends AtlasDbImmutableTable<ROW, COLUMN_VALUE, ROW_RESULT> {
    List<ROW_RESULT> getRows(Iterable<ROW> rows);

    List<ROW_RESULT> getRows(Iterable<ROW> rows, ColumnSelection columnSelection);
}
