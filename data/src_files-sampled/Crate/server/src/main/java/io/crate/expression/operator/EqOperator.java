package io.crate.expression.operator;

import io.crate.data.Input;
import io.crate.metadata.NodeContext;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;

import static io.crate.metadata.functions.TypeVariableConstraint.typeVariable;
import static io.crate.types.TypeSignature.parseTypeSignature;

public final class EqOperator extends Operator<Object> {

    public static final String NAME = "op_=";

    public static final Signature SIGNATURE = Signature.scalar(
        NAME,
        parseTypeSignature("E"),
        parseTypeSignature("E"),
        Operator.RETURN_TYPE.getTypeSignature()
    ).withTypeVariableConstraints(typeVariable("E"));

    public static void register(OperatorModule module) {
        module.register(
            SIGNATURE,
            EqOperator::new
        );
    }

    private final Signature signature;
    private final Signature boundSignature;

    private EqOperator(Signature signature, Signature boundSignature) {
        this.signature = signature;
        this.boundSignature = boundSignature;
    }

    @Override
    public Boolean evaluate(TransactionContext txnCtx, NodeContext nodeCtx, Input<Object>[] args) {
        assert args.length == 2 : "number of args must be 2";
        Object left = args[0].value();
        if (left == null) {
            return null;
        }
        Object right = args[1].value();
        if (right == null) {
            return null;
        }
        return left.equals(right);
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
