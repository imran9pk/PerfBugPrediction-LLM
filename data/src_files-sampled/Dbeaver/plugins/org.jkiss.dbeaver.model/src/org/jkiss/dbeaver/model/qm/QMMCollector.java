package org.jkiss.dbeaver.model.qm;

import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.qm.meta.QMMSessionInfo;

public interface QMMCollector extends QMExecutionHandler {

    QMMSessionInfo getSessionInfo(DBCExecutionContext context);
}
