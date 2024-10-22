package org.ballerinalang.langlib.string;

import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;

@BallerinaFunction(
        orgName = "ballerina", packageName = "lang.string", functionName = "toCodePointInt",
        args = {@Argument(name = "ch", type = TypeKind.STRING)},
        returnType = {@ReturnType(type = TypeKind.INT)},
        isPublic = true
)
public class ToCodePointInt {

    public static long toCodePointInt(Strand strand, String ch) {
        return ch.codePointAt(0);
    }
}
