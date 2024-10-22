package org.wso2.ballerinalang.compiler.tree.statements;

import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.model.tree.statements.ReturnNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;

public class BLangReturn extends BLangStatement implements ReturnNode {
    public BLangExpression expr;

    @Override
    public BLangExpression getExpression() {
        return expr;
    }

    @Override
    public void setExpression(ExpressionNode expressionNode) {
        this.expr = (BLangExpression) expressionNode;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.RETURN;
    }

    @Override
    public String toString() {
        return "Return: " + (this.expr != null ? this.expr : "");
    }

}
