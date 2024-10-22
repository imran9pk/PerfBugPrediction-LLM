package io.crate.planner.optimizer.rule;

import io.crate.expression.symbol.FieldReplacer;
import io.crate.expression.symbol.Symbol;
import io.crate.metadata.NodeContext;
import io.crate.metadata.TransactionContext;
import io.crate.statistics.TableStats;
import io.crate.planner.operators.Filter;
import io.crate.planner.operators.LogicalPlan;
import io.crate.planner.operators.Union;
import io.crate.planner.optimizer.Rule;
import io.crate.planner.optimizer.matcher.Capture;
import io.crate.planner.optimizer.matcher.Captures;
import io.crate.planner.optimizer.matcher.Pattern;

import java.util.List;

import static io.crate.planner.optimizer.matcher.Pattern.typeOf;
import static io.crate.planner.optimizer.matcher.Patterns.source;

public final class MoveFilterBeneathUnion implements Rule<Filter> {

    private final Capture<Union> unionCapture;
    private final Pattern<Filter> pattern;

    public MoveFilterBeneathUnion() {
        this.unionCapture = new Capture<>();
        this.pattern = typeOf(Filter.class)
            .with(source(), typeOf(Union.class).capturedAs(unionCapture));
    }

    @Override
    public Pattern<Filter> pattern() {
        return pattern;
    }

    @Override
    public LogicalPlan apply(Filter filter,
                             Captures captures,
                             TableStats tableStats,
                             TransactionContext txnCtx,
                             NodeContext nodeCtx) {
        Union union = captures.get(unionCapture);
        LogicalPlan lhs = union.sources().get(0);
        LogicalPlan rhs = union.sources().get(1);
        return union.replaceSources(List.of(
            createNewFilter(filter, lhs),
            createNewFilter(filter, rhs)
        ));
    }

    private static Filter createNewFilter(Filter filter, LogicalPlan newSource) {
        Symbol newQuery = FieldReplacer.replaceFields(filter.query(), f -> {
            int idx = filter.source().outputs().indexOf(f);
            if (idx < 0) {
                throw new IllegalArgumentException(
                    "Field used in filter must be present in its source outputs." +
                    f + " missing in " + filter.source().outputs());
            }
            return newSource.outputs().get(idx);
        });
        return new Filter(newSource, newQuery);
    }
}
