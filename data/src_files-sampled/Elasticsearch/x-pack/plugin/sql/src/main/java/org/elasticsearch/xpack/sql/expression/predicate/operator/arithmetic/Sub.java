package org.elasticsearch.xpack.sql.expression.predicate.operator.arithmetic;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.predicate.operator.arithmetic.BinaryArithmeticProcessor.BinaryArithmeticOperation;
import org.elasticsearch.xpack.sql.tree.NodeInfo;
import org.elasticsearch.xpack.sql.tree.Source;
import org.elasticsearch.xpack.sql.type.DataTypes;

import static org.elasticsearch.common.logging.LoggerMessageFormat.format;

public class Sub extends DateTimeArithmeticOperation {

    public Sub(Source source, Expression left, Expression right) {
        super(source, left, right, BinaryArithmeticOperation.SUB);
    }

    @Override
    protected NodeInfo<Sub> info() {
        return NodeInfo.create(this, Sub::new, left(), right());
    }

    @Override
    protected Sub replaceChildren(Expression newLeft, Expression newRight) {
        return new Sub(source(), newLeft, newRight);
    }

    @Override
    protected TypeResolution resolveWithIntervals() {
        TypeResolution resolution = super.resolveWithIntervals();
        if (resolution.unresolved()) {
            return resolution;
        }
        if ((right().dataType().isDateBased()) && DataTypes.isInterval(left().dataType())) {
            return new TypeResolution(format(null, "Cannot subtract a {}[{}] from an interval[{}]; do you mean the reverse?",
                right().dataType().typeName, right().source().text(), left().source().text()));
        }
        return TypeResolution.TYPE_RESOLVED;
    }
}
