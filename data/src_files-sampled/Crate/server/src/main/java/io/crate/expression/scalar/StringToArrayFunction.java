package io.crate.expression.scalar;

import io.crate.data.Input;
import io.crate.metadata.NodeContext;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;
import io.crate.types.DataTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StringToArrayFunction extends Scalar<List<String>, String> {

    private static final String NAME = "string_to_array";

    public static void register(ScalarFunctionModule module) {
        module.register(
            Signature.scalar(
                NAME,
                DataTypes.STRING.getTypeSignature(),
                DataTypes.STRING.getTypeSignature(),
                DataTypes.STRING_ARRAY.getTypeSignature()
            ),
            StringToArrayFunction::new
        );
        module.register(
            Signature.scalar(
                NAME,
                DataTypes.STRING.getTypeSignature(),
                DataTypes.STRING.getTypeSignature(),
                DataTypes.STRING.getTypeSignature(),
                DataTypes.STRING_ARRAY.getTypeSignature()
            ),
            StringToArrayFunction::new
        );
    }

    private final Signature signature;
    private final Signature boundSignature;

    public StringToArrayFunction(Signature signature, Signature boundSignature) {
        this.signature = signature;
        this.boundSignature = boundSignature;
    }

    @Override
    public Signature signature() {
        return signature;
    }

    @Override
    public Signature boundSignature() {
        return boundSignature;
    }

    @Override
    public List<String> evaluate(TransactionContext txnCtx, NodeContext nodeCtx, Input<String>[] args) {
        assert args.length == 2 || args.length == 3 : "number of args must be 2 or 3";

        String str = args[0].value();
        if (str == null) {
            return null;
        }
        if (str.isEmpty()) {
            return List.of();
        }

        String separator = args[1].value();
        String nullStr = null;
        if (args.length == 3) {
            nullStr = args[2].value();
        }

        return split(str, separator, nullStr);
    }

    private static List<String> split(@Nonnull String str, @Nullable String separator, @Nullable String nullStr) {

        if (separator == null) {
            ArrayList<String> subStrings = new ArrayList<>(str.length());
            for (int i = 0; i < str.length(); i++) {
                String subStr = String.valueOf(str.charAt(i));
                subStrings.add(setToNullIfMatch(subStr, nullStr));
            }
            return subStrings;
        } else if (separator.isEmpty()) {
            return Collections.singletonList(setToNullIfMatch(str, nullStr));
        } else {
            ArrayList<String> subStrings = new ArrayList<>();
            int start = 0;                      int pos = str.indexOf(separator);   while (pos >= start) {
                String subStr;

                if (pos > start) {
                    subStr = str.substring(start, pos);
                } else {
                    subStr = "";
                }

                start = pos + separator.length();
                pos = str.indexOf(separator, start);

                subStrings.add(setToNullIfMatch(subStr, nullStr));
            }
            String subStr = str.substring(start);
            subStrings.add(setToNullIfMatch(subStr, nullStr));
            return subStrings;
        }
    }

    @Nullable
    private static String setToNullIfMatch(String subStr, String nullStr) {
        if (Objects.equals(subStr, nullStr)) {
            return null;
        }
        return subStr;
    }
}
