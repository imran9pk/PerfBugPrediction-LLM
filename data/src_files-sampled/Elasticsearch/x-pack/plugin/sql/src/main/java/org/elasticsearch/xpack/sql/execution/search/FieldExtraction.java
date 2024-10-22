package org.elasticsearch.xpack.sql.execution.search;

import org.elasticsearch.search.builder.SearchSourceBuilder;

public interface FieldExtraction {

    void collectFields(SqlSourceBuilder sourceBuilder);

    boolean supportedByAggsOnlyQuery();
}
