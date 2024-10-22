package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Globals;
import org.elasticsearch.painless.Locals;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.MethodWriter;

import java.util.Objects;
import java.util.Set;

public final class SExpression extends AStatement {

    private AExpression expression;

    public SExpression(Location location, AExpression expression) {
        super(location);

        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    void extractVariables(Set<String> variables) {
        expression.extractVariables(variables);
    }

    @Override
    void analyze(Locals locals) {
        Class<?> rtnType = locals.getReturnType();
        boolean isVoid = rtnType == void.class;

        expression.read = lastSource && !isVoid;
        expression.analyze(locals);

        if (!lastSource && !expression.statement) {
            throw createError(new IllegalArgumentException("Not a statement."));
        }

        boolean rtn = lastSource && !isVoid && expression.actual != void.class;

        expression.expected = rtn ? rtnType : expression.actual;
        expression.internal = rtn;
        expression = expression.cast(locals);

        methodEscape = rtn;
        loopEscape = rtn;
        allEscape = rtn;
        statementCount = 1;
    }

    @Override
    void write(MethodWriter writer, Globals globals) {
        writer.writeStatementOffset(location);
        expression.write(writer, globals);

        if (methodEscape) {
            writer.returnValue();
        } else {
            writer.writePop(MethodWriter.getType(expression.expected).getSize());
        }
    }

    @Override
    public String toString() {
        return singleLineToString(expression);
    }
}
