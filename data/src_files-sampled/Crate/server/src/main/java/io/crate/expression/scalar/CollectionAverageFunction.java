package io.crate.expression.scalar;

import io.crate.data.Input;
import io.crate.metadata.NodeContext;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;
import io.crate.types.DataTypes;

import java.util.List;

import static io.crate.metadata.functions.TypeVariableConstraint.typeVariable;
import static io.crate.types.TypeSignature.parseTypeSignature;

public class CollectionAverageFunction extends Scalar<Double, List<Object>> {

    public static final String NAME = "collection_avg";

    public static void register(ScalarFunctionModule module) {
        module.register(
            Signature.scalar(
                NAME,
                parseTypeSignature("array(E)"),
                DataTypes.DOUBLE.getTypeSignature()
            ).withTypeVariableConstraints(typeVariable("E")),
            CollectionAverageFunction::new
        );
    }

    private final Signature signature;
    private final Signature boundSignature;

    private CollectionAverageFunction(Signature signature, Signature boundSignature) {
        this.signature = signature;
        this.boundSignature = boundSignature;
    }

    @Override
    public Double evaluate(TransactionContext txnCtx, NodeContext nodeCtx, Input<List<Object>>... args) {
        List<Object> values = args[0].value();
        if (values == null) {
            return null;
        }
        double sum = 0;
        long count = 0;
        for (Object value : values) {
            sum += ((Number) value).doubleValue();
            count++;
        }
        if (count > 0) {
            return sum / count;
        } else {
            return null;
        }
    }

    @Override
    public Signature signature() {
        return signature;
    }

    @Override
    public Signature boundSignature() {
        return boundSignature;
    }
}
