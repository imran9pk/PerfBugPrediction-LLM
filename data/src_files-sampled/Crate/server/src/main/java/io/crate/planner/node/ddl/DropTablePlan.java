package io.crate.planner.node.ddl;

import io.crate.analyze.AnalyzedDropTable;
import io.crate.common.annotations.VisibleForTesting;
import io.crate.data.InMemoryBatchIterator;
import io.crate.data.Row;
import io.crate.data.Row1;
import io.crate.data.RowConsumer;
import io.crate.execution.ddl.tables.DropTableRequest;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.metadata.table.TableInfo;
import io.crate.planner.DependencyCarrier;
import io.crate.planner.Plan;
import io.crate.planner.PlannerContext;
import io.crate.planner.operators.SubQueryResults;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.index.IndexNotFoundException;

import static io.crate.data.SentinelRow.SENTINEL;

public class DropTablePlan implements Plan {

    private static final Logger LOGGER = LogManager.getLogger(DropTablePlan.class);
    private static final Row ROW_ZERO = new Row1(0L);
    private static final Row ROW_ONE = new Row1(1L);

    private final AnalyzedDropTable<?> dropTable;

    public DropTablePlan(AnalyzedDropTable<?> dropTable) {
        this.dropTable = dropTable;
    }

    @VisibleForTesting
    public TableInfo tableInfo() {
        return dropTable.table();
    }

    @VisibleForTesting
    public AnalyzedDropTable<?> dropTable() {
        return dropTable;
    }

    @Override
    public StatementType type() {
        return StatementType.DDL;
    }


    @Override
    public void executeOrFail(DependencyCarrier dependencies,
                              PlannerContext plannerContext,
                              RowConsumer consumer,
                              Row params,
                              SubQueryResults subQueryResults) {
        TableInfo table = dropTable.table();
        DropTableRequest request;
        if (table == null) {
            if (dropTable.maybeCorrupt()) {
                request = new DropTableRequest(dropTable.tableName(), true);
            } else {
                assert dropTable.dropIfExists() : "If table is null, IF EXISTS flag must have been present";
                consumer.accept(InMemoryBatchIterator.of(ROW_ZERO, SENTINEL), null);
                return;
            }
        } else {
            boolean isPartitioned = table instanceof DocTableInfo && ((DocTableInfo) table).isPartitioned();
            request = new DropTableRequest(table.ident(), isPartitioned);
        }
        dependencies.transportDropTableAction().execute(request, new ActionListener<>() {
            @Override
            public void onResponse(AcknowledgedResponse response) {
                if (!response.isAcknowledged() && LOGGER.isWarnEnabled()) {
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Dropping table {} was not acknowledged. This could lead to inconsistent state.", dropTable.tableName());
                    }
                }
                consumer.accept(InMemoryBatchIterator.of(ROW_ONE, SENTINEL), null);
            }

            @Override
            public void onFailure(Exception e) {
                if (dropTable.dropIfExists() && e instanceof IndexNotFoundException) {
                    consumer.accept(InMemoryBatchIterator.of(ROW_ZERO, SENTINEL), null);
                } else {
                    consumer.accept(null, e);
                }
            }
        });
    }
}
