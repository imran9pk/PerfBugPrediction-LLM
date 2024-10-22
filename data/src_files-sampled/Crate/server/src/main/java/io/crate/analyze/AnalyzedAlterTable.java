package io.crate.analyze;

import io.crate.expression.symbol.Symbol;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.sql.tree.AlterTable;
import io.crate.sql.tree.Assignment;

import java.util.function.Consumer;

public class AnalyzedAlterTable implements DDLStatement {

    private final DocTableInfo tableInfo;
    private final AlterTable<Symbol> alterTable;

    public AnalyzedAlterTable(DocTableInfo tableInfo,
                              AlterTable<Symbol> alterTable) {
        this.tableInfo = tableInfo;
        this.alterTable = alterTable;
    }

    public DocTableInfo tableInfo() {
        return tableInfo;
    }

    public AlterTable<Symbol> alterTable() {
        return alterTable;
    }

    @Override
    public void visitSymbols(Consumer<? super Symbol> consumer) {
        for (Assignment<Symbol> partitionProperty : alterTable.table().partitionProperties()) {
            consumer.accept(partitionProperty.expression());
            partitionProperty.expressions().forEach(consumer);
        }
        alterTable.genericProperties().properties().values().forEach(consumer);
    }

    @Override
    public <C, R> R accept(AnalyzedStatementVisitor<C, R> visitor, C context) {
        return visitor.visitAlterTable(this, context);
    }
}
