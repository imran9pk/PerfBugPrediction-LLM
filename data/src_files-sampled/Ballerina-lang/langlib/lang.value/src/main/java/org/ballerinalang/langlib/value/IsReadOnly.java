package org.ballerinalang.langlib.value;

import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.RefValue;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;

@BallerinaFunction(
        orgName = "ballerina",
        packageName = "lang.value",
        functionName = "isReadOnly",
        args = {@Argument(name = "value", type = TypeKind.ANYDATA)},
        returnType = { @ReturnType(type = TypeKind.BOOLEAN) }
)
public class IsReadOnly {

    public static boolean isReadOnly(Strand strand, Object value) {
        return !(value instanceof RefValue) || ((RefValue) value).isFrozen();
    }
}
