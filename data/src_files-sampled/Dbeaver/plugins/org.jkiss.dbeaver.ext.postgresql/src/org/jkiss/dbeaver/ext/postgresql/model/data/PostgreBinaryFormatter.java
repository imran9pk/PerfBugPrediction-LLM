package org.jkiss.dbeaver.ext.postgresql.model.data;

import org.jkiss.dbeaver.model.impl.data.formatters.BinaryFormatterHex;

public class PostgreBinaryFormatter extends BinaryFormatterHex {

    public static final PostgreBinaryFormatter INSTANCE = new PostgreBinaryFormatter();
    private static final String HEX_PREFIX = "decode('";
    private static final String HEX_POSTFIX = "','hex')";

    @Override
    public String getId()
    {
        return "pghex";
    }

    @Override
    public String getTitle()
    {
        return "PostgreSQL Hex";
    }

    @Override
    public String toString(byte[] bytes, int offset, int length)
    {
        return HEX_PREFIX + super.toString(bytes, offset, length) + HEX_POSTFIX;
    }

    @Override
    public byte[] toBytes(String string)
    {
        if (string.startsWith(HEX_PREFIX)) {
            string = string.substring(
                HEX_PREFIX.length(),
                string.length() - HEX_POSTFIX.length());
        }
        return super.toBytes(string);
    }

}
