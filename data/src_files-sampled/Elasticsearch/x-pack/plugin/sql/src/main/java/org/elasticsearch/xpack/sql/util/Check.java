package org.elasticsearch.xpack.sql.util;

import org.elasticsearch.xpack.sql.SqlIllegalArgumentException;

public abstract class Check {

    public static void isTrue(boolean expression, String message, Object... values) {
        if (!expression) {
            throw new SqlIllegalArgumentException(message, values);
        }
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new SqlIllegalArgumentException(message);
        }
    }

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new SqlIllegalArgumentException(message);
        }
    }

    public static void notNull(Object object, String message, Object... values) {
        if (object == null) {
            throw new SqlIllegalArgumentException(message, values);
        }
    }
}
