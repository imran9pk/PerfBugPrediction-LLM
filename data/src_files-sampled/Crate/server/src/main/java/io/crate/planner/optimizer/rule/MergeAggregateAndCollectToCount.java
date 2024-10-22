package io.crate.planner.optimizer.rule;

import io.crate.common.collections.Lists2;
import io.crate.execution.engine.aggregation.impl.CountAggregation;
import io.crate.metadata.NodeContext;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.statistics.TableStats;
import io.crate.planner.operators.Collect;
import io.crate.planner.operators.Count;
import io.crate.planner.operators.HashAggregate;
import io.crate.planner.optimizer.Rule;
import io.crate.planner.optimizer.matcher.Capture;
import io.crate.planner.optimizer.matcher.Captures;
import io.crate.planner.optimizer.matcher.Pattern;

import static io.crate.planner.optimizer.matcher.Pattern.typeOf;
import static io.crate.planner.optimizer.matcher.Patterns.source;

public final class MergeAggregateAndCollectToCount implements Rule<HashAggregate> {

    private final Capture<Collect> collectCapture;
    private final Pattern<HashAggregate> pattern;

    public MergeAggregateAndCollectToCount() {
        this.collectCapture = new Capture<>();
        this.pattern = typeOf(HashAggregate.class)
            .with(source(), typeOf(Collect.class).capturedAs(collectCapture)
                .with(collect -> collect.relation().tableInfo() instanceof DocTableInfo))
            .with(aggregate ->
                aggregate.aggregates().size() == 1
                && aggregate.aggregates().get(0).signature().equals(CountAggregation.COUNT_STAR_SIGNATURE));
    }

    @Override
    public Pattern<HashAggregate> pattern() {
        return pattern;
    }

    @Override
    public Count apply(HashAggregate aggregate,
                       Captures captures,
                       TableStats tableStats,
                       TransactionContext txnCtx,
                       NodeContext nodeCtx) {
        Collect collect = captures.get(collectCapture);
        var countAggregate = Lists2.getOnlyElement(aggregate.aggregates());
        if (countAggregate.filter() != null) {
            return new Count(
                countAggregate,
                collect.relation(),
                collect.where().add(countAggregate.filter()));
        } else {
            return new Count(countAggregate, collect.relation(), collect.where());
        }
    }
}
