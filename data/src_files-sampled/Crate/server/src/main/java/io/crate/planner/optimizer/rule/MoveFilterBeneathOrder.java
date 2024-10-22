package io.crate.planner.optimizer.rule;

import io.crate.metadata.NodeContext;
import io.crate.metadata.TransactionContext;
import io.crate.statistics.TableStats;
import io.crate.planner.operators.Filter;
import io.crate.planner.operators.LogicalPlan;
import io.crate.planner.operators.Order;
import io.crate.planner.optimizer.Rule;
import io.crate.planner.optimizer.matcher.Capture;
import io.crate.planner.optimizer.matcher.Captures;
import io.crate.planner.optimizer.matcher.Pattern;

import static io.crate.planner.optimizer.matcher.Pattern.typeOf;
import static io.crate.planner.optimizer.matcher.Patterns.source;
import static io.crate.planner.optimizer.rule.Util.transpose;

public final class MoveFilterBeneathOrder implements Rule<Filter> {

    private final Capture<Order> orderCapture;
    private final Pattern<Filter> pattern;

    public MoveFilterBeneathOrder() {
        this.orderCapture = new Capture<>();
        this.pattern = typeOf(Filter.class)
            .with(source(), typeOf(Order.class).capturedAs(orderCapture));
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
        return transpose(filter, captures.get(orderCapture));
    }
}
