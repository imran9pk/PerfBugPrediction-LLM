package io.crate.expression;

import io.crate.data.Row;
import io.crate.metadata.ColumnIdent;
import io.crate.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.crate.common.collections.Maps.getByPath;


public final class ValueExtractors {

    public static Object fromMap(Map<String, Object> map, ColumnIdent column) {
        Object o = map.get(column.name());
        if (column.isTopLevel()) {
            return o;
        }
        if (o instanceof Map) {
            return getByPath((Map) o, column.path());
        }
        if (o instanceof List) {
            List<?> values = (List<?>) o;
            ArrayList<Object> extractedValues = new ArrayList<>(values.size());
            for (Object value : values) {
                if (value instanceof Map) {
                    extractedValues.add(getByPath((Map) value, column.path()));
                } else {
                    extractedValues.add(value);
                }
            }
            return extractedValues;
        }
        return o;
    }

    public static Function<Map<String, Object>, Object> fromMap(ColumnIdent column, DataType<?> type) {
        return map -> type.implicitCast(fromMap(map, column));
    }

    public static Function<Row, Object> fromRow(int idx, List<String> subscript) {
        return new FromRowWithSubscript(idx, subscript);
    }

    private static class FromRowWithSubscript implements Function<Row, Object> {
        private final int idx;
        private final List<String> subscript;

        FromRowWithSubscript(int idx, List<String> subscript) {
            this.idx = idx;
            this.subscript = subscript;
        }

        @Override
        public Object apply(Row row) {
            Object o = row.get(idx);
            if (o instanceof Map) {
                return getByPath((Map) o, subscript);
            }
            return null;
        }
    }
}
