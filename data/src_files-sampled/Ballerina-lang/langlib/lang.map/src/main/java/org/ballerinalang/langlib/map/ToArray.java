package org.ballerinalang.langlib.map;

import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BMapType;
import org.ballerinalang.jvm.types.BRecordType;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.TypeTags;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ArrayValueImpl;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.langlib.map.util.MapLibUtils;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;

import java.util.Collection;

import static org.ballerinalang.jvm.MapUtils.createOpNotSupportedError;

@BallerinaFunction(
        orgName = "ballerina", packageName = "lang.map",
        functionName = "toArray",
        args = {@Argument(name = "m", type = TypeKind.MAP)},
        returnType = {@ReturnType(type = TypeKind.ARRAY, elementType = TypeKind.ANY)},
        isPublic = true
)
public class ToArray {

    public static ArrayValue toArray(Strand strand, MapValue<?, ?> m) {
        BType mapType = m.getType();
        BType arrElemType;
        switch (mapType.getTag()) {
            case TypeTags.MAP_TAG:
                arrElemType = ((BMapType) mapType).getConstrainedType();
                break;
            case TypeTags.RECORD_TYPE_TAG:
                arrElemType = MapLibUtils.getCommonTypeForRecordField((BRecordType) mapType);
                break;
            default:
                throw createOpNotSupportedError(mapType, "toArray()");
        }

        Collection values = m.values();
        int size = values.size();
        int i = 0;
        switch (arrElemType.getTag()) {
            case TypeTags.INT_TAG:
                long[] intArr = new long[size];
                for (Object val : values) {
                    intArr[i++] = (Long) val;
                }
                return new ArrayValueImpl(intArr);
            case TypeTags.FLOAT_TAG:
                double[] floatArr = new double[size];
                for (Object val : values) {
                    floatArr[i++] = (Double) val;
                }
                return new ArrayValueImpl(floatArr);
            case TypeTags.BYTE_TAG:
                byte[] byteArr = new byte[size];
                for (Object val : values) {
                    byteArr[i++] = ((Integer) val).byteValue();
                }
                return new ArrayValueImpl(byteArr);
            case TypeTags.BOOLEAN_TAG:
                boolean[] booleanArr = new boolean[size];
                for (Object val : values) {
                    booleanArr[i++] = (Boolean) val;
                }
                return new ArrayValueImpl(booleanArr);
            case TypeTags.STRING_TAG:
                String[] stringArr = new String[size];
                for (Object val : values) {
                    stringArr[i++] = (String) val;
                }
                return new ArrayValueImpl(stringArr);
            default:
                return new ArrayValueImpl(values.toArray(), new BArrayType(arrElemType));

        }
    }
}
