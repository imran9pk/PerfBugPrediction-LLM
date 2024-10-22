package org.wso2.ballerinalang.compiler.tree.expressions;

import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.ElvisExpressionNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;

public class BLangElvisExpr extends BLangExpression implements ElvisExpressionNode {

    public BLangExpression lhsExpr;
    public BLangExpression rhsExpr;

    @Override
    public BLangExpression getLeftExpression() {
        return lhsExpr;
    }

    @Override
    public BLangExpression getRightExpression() {
        return rhsExpr;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.ELVIS_EXPR;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return String.valueOf(lhsExpr) + "?:" + String.valueOf(rhsExpr);
    }
}
