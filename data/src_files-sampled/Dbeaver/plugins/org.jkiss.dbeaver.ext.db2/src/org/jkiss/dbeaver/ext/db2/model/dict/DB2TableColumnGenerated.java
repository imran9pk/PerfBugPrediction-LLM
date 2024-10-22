package org.jkiss.dbeaver.ext.db2.model.dict;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPNamedObject;

public enum DB2TableColumnGenerated implements DBPNamedObject {
    A("Always"),

    D("By Default");

    private String name;

    private DB2TableColumnGenerated(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }

    @NotNull
    @Override
    public String getName()
    {
        return name;
    }
}