package org.jkiss.dbeaver.ext.mssql.model;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Top;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformer;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformerExt;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.sql.SQLQuery;
import org.jkiss.utils.CommonUtils;

public class QueryTransformerTop implements DBCQueryTransformer, DBCQueryTransformerExt {

    private static final Log log = Log.getLog(QueryTransformerTop.class);

    private Number offset;
    private Number length;
    private boolean limitSet;

    @Override
    public void setParameters(Object... parameters) {
        this.offset = (Number) parameters[0];
        this.length = (Number) parameters[1];
    }

    @Override
    public String transformQueryString(SQLQuery query) throws DBCException {
        limitSet = false;
        if (query.isPlainSelect()) {
            try {
                Statement statement = query.getStatement();
                if (statement instanceof Select) {
                    Select select = (Select) statement;
                    if (select.getSelectBody() instanceof PlainSelect) {
                        PlainSelect selectBody = (PlainSelect) select.getSelectBody();
                        if (selectBody.getTop() == null && CommonUtils.isEmpty(selectBody.getIntoTables())) {
                            Top top = new Top();
                            top.setPercentage(false);
                            top.setExpression(new LongValue(offset.longValue() + length.longValue()));
                            selectBody.setTop(top);

                            limitSet = true;
                            return statement.toString();
                        }
                    }
                }
            } catch (Throwable e) {
                log.debug(e);
            }
        }
        return query.getText();
    }

    @Override
    public void transformStatement(DBCStatement statement, int parameterIndex) throws DBCException {
        if (!limitSet) {
            statement.setLimit(offset.longValue(), length.longValue());
        }
    }

    @Override
    public boolean isApplicableTo(SQLQuery query) {
        if (query.isPlainSelect()) {
            final Statement statement = query.getStatement();
            if (statement instanceof Select) {
                final SelectBody body = ((Select) statement).getSelectBody();
                if (body instanceof PlainSelect) {
                    return ((PlainSelect) body).getOffset() == null;
                }
            }
        }
        return false;
    }
}