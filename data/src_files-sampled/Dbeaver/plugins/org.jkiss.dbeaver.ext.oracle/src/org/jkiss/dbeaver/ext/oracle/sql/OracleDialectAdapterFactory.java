package org.jkiss.dbeaver.ext.oracle.sql;

import org.eclipse.core.runtime.IAdapterFactory;
import org.jkiss.dbeaver.model.sql.SQLDialect;
import org.jkiss.dbeaver.model.text.parser.TPRuleProvider;

public class OracleDialectAdapterFactory implements IAdapterFactory {

    private static final Class<?>[] CLASSES = new Class[] { TPRuleProvider.class };
    
    @Override
    public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
        if (adaptableObject instanceof SQLDialect) {
            if (adapterType == TPRuleProvider.class) {
                return adapterType.cast(new OracleDialectRules());
            }
        }
        return null;
    }

    @Override
    public Class<?>[] getAdapterList() {
        return CLASSES;
    }

}
