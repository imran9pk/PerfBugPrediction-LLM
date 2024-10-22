package io.crate.analyze;

import io.crate.expression.symbol.Symbol;

import java.util.function.Consumer;

public final class AnalyzedAnalyze implements DDLStatement {

    AnalyzedAnalyze() {
    }

    @Override
    public <C, R> R accept(AnalyzedStatementVisitor<C, R> visitor, C context) {
        return visitor.visitAnalyze(this, context);
    }

    @Override
    public void visitSymbols(Consumer<? super Symbol> consumer) {
    }
}
