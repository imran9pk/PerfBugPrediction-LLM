package org.wso2.ballerinalang.compiler.tree.expressions;

import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.ArrowFunctionNode;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangExprFunctionBody;
import org.wso2.ballerinalang.compiler.tree.BLangInvokableNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.util.ClosureVarSymbol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class BLangArrowFunction extends BLangExpression implements ArrowFunctionNode {

    public List<BLangSimpleVariable> params = new ArrayList<>();
    public BType funcType;
    public IdentifierNode functionName;
    public BLangInvokableNode function;
    public BLangExprFunctionBody body;

    public Set<ClosureVarSymbol> closureVarSymbols = new LinkedHashSet<>();

    @Override
    public List<BLangSimpleVariable> getParameters() {
        return params;
    }

    @Override
    public BLangExprFunctionBody getBody() {
        return this.body;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.ARROW_EXPR;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return String.format("ArrowExprRef:(%s) => %s",
                             Arrays.toString(params.stream().map(x -> x.name).toArray()), body.expr);
    }
}
