package io.crate.planner.node.management;

import io.crate.analyze.AnalyzedShowCreateTable;
import io.crate.analyze.MetadataToASTNodeResolver;
import io.crate.data.InMemoryBatchIterator;
import io.crate.data.Row;
import io.crate.data.Row1;
import io.crate.data.RowConsumer;
import io.crate.planner.DependencyCarrier;
import io.crate.planner.Plan;
import io.crate.planner.PlannerContext;
import io.crate.planner.operators.SubQueryResults;
import io.crate.sql.SqlFormatter;
import io.crate.sql.tree.CreateTable;

import static io.crate.data.SentinelRow.SENTINEL;

public class ShowCreateTablePlan implements Plan {

    private final AnalyzedShowCreateTable statement;

    public ShowCreateTablePlan(AnalyzedShowCreateTable statement) {
        this.statement = statement;
    }

    @Override
    public StatementType type() {
        return StatementType.MANAGEMENT;
    }

    @Override
    public void executeOrFail(DependencyCarrier dependencies,
                              PlannerContext plannerContext,
                              RowConsumer consumer,
                              Row params,
                              SubQueryResults subQueryResults) {
        Row1 row;
        try {
            CreateTable createTable = MetadataToASTNodeResolver.resolveCreateTable(statement.tableInfo());
            row = new Row1(SqlFormatter.formatSql(createTable));
        } catch (Throwable t) {
            consumer.accept(null, t);
            return;
        }
        consumer.accept(InMemoryBatchIterator.of(row, SENTINEL), null);
    }
}
