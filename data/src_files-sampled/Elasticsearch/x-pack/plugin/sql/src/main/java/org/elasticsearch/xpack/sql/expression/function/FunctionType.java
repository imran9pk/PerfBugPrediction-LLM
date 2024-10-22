package org.elasticsearch.xpack.sql.expression.function;

import org.elasticsearch.xpack.sql.SqlIllegalArgumentException;
import org.elasticsearch.xpack.sql.expression.function.aggregate.AggregateFunction;
import org.elasticsearch.xpack.sql.expression.function.grouping.GroupingFunction;
import org.elasticsearch.xpack.sql.expression.function.scalar.ScalarFunction;
import org.elasticsearch.xpack.sql.expression.predicate.conditional.ConditionalFunction;


public enum FunctionType {

    AGGREGATE(AggregateFunction.class),
    CONDITIONAL(ConditionalFunction.class),
    GROUPING(GroupingFunction.class),
    SCALAR(ScalarFunction.class),
    SCORE(Score.class);

    private final Class<? extends Function> baseClass;

    FunctionType(Class<? extends Function> base) {
        this.baseClass = base;
    }

    public static FunctionType of(Class<? extends Function> clazz) {
        for (FunctionType type : values()) {
            if (type.baseClass.isAssignableFrom(clazz)) {
                return type;
            }
        }
        throw new SqlIllegalArgumentException("Cannot identify the function type for {}", clazz);
    }
}
