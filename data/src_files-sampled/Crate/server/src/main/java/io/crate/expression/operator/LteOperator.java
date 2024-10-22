package io.crate.expression.operator;

import io.crate.metadata.functions.Signature;
import io.crate.types.DataTypes;

public final class LteOperator {

    public static final String NAME = "op_<=";

    public static void register(OperatorModule module) {
        for (var supportedType : DataTypes.PRIMITIVE_TYPES) {
            module.register(
                Signature.scalar(
                    NAME,
                    supportedType.getTypeSignature(),
                    supportedType.getTypeSignature(),
                    Operator.RETURN_TYPE.getTypeSignature()
                ),
                (signature, boundSignature) -> new CmpOperator(
                    signature,
                    boundSignature,
                    cmpResult -> cmpResult <= 0
                )
            );
        }
    }
}
