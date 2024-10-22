package org.wso2.ballerinalang.compiler.tree.expressions;

import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.model.tree.expressions.TernaryExpressionNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;

public class BLangTernaryExpr extends BLangExpression implements TernaryExpressionNode {

    public BLangExpression expr;
    public BLangExpression thenExpr;
    public BLangExpression elseExpr;

    @Override
    public ExpressionNode getCondition() {
        return expr;
    }

    @Override
    public ExpressionNode getThenExpression() {
        return thenExpr;
    }

    @Override
    public ExpressionNode getElseExpression() {
        return elseExpr;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.TERNARY_EXPR;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return String.valueOf(expr) + "?" + String.valueOf(thenExpr) + ":" + String.valueOf(elseExpr);
    }
}
