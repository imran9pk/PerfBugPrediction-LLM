package org.wso2.ballerinalang.compiler.tree.expressions;

import org.ballerinalang.model.Whitespace;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.XMLNavigationAccess;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class BLangXMLNavigationAccess extends BLangAccessExpression implements XMLNavigationAccess {

    public final NavAccessType navAccessType;
    public final List<BLangXMLElementFilter> filters;
    public BLangExpression childIndex;
    public boolean methodInvocationAnalyzed;

    public BLangXMLNavigationAccess(DiagnosticPos pos, Set<Whitespace> ws, BLangExpression expr,
                                    List<BLangXMLElementFilter> filters,
                                    NavAccessType navAccessType,
                                    BLangExpression childIndex) {
        this.pos = pos;
        this.addWS(ws);
        this.expr = expr;
        this.filters = filters;
        this.navAccessType = navAccessType;
        this.childIndex = childIndex;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.XML_NAVIGATION;
    }

    @Override
    public NavAccessType getNavAccessType() {
        return navAccessType;
    }

    @Override
    public List<BLangXMLElementFilter> getFilters() {
        return this.filters;
    }

    @Override
    public BLangExpression getExpression() {
        return this.expr;
    }

    @Override
    public BLangExpression getChildIndex() {
        return this.childIndex;
    }

    @Override
    public String toString() {
        switch (navAccessType) {
            case CHILDREN:
                return String.valueOf(expr) + "/*";
            case CHILD_ELEMS:
                StringJoiner filters = new StringJoiner(" |");
                this.filters.forEach(f -> filters.toString());
                return String.valueOf(expr) + "/<" + filters.toString() + ">" +
                        (childIndex != null ? "[" + String.valueOf(childIndex) + "]" : "");
        }
        return null;
    }
}
