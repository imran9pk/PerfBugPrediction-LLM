package org.elasticsearch.xpack.sql.expression;

public enum Nullability {
    TRUE,    FALSE,   UNKNOWN; public static Nullability and(Nullability... nullabilities) {
        Nullability value = null;
        for (Nullability n: nullabilities) {
            switch (n) {
                case UNKNOWN:
                    return UNKNOWN;
                case TRUE:
                    value = TRUE;
                    break;
                case FALSE:
                    if (value == null) {
                        value = FALSE;
                    }
            }
        }
        return value != null ? value : FALSE;
    }
}
