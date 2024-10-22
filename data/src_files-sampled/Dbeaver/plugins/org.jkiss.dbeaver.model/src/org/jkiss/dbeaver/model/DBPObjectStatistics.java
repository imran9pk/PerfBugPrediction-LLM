package org.jkiss.dbeaver.model;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.preferences.DBPPropertySource;

public interface DBPObjectStatistics {

    boolean hasStatistics();

    long getStatObjectSize();

    @Nullable
    DBPPropertySource getStatProperties();

}
