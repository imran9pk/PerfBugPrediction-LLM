package io.crate.execution.engine.aggregation;

import io.crate.breaker.RamAccounting;
import io.crate.data.Input;
import io.crate.data.Row;
import io.crate.data.RowN;
import io.crate.execution.engine.collect.CollectExpression;
import io.crate.expression.InputCondition;
import io.crate.expression.symbol.AggregateMode;
import io.crate.memory.MemoryManager;
import org.elasticsearch.Version;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class AggregateCollector implements Collector<Row, Object[], Iterable<Row>> {

    private final List<? extends CollectExpression<Row, ?>> expressions;
    private final RamAccounting ramAccounting;
    private final MemoryManager memoryManager;
    private final AggregationFunction[] aggregations;
    private final Version indexVersionCreated;
    private final Input<Boolean>[] filters;
    private final Input[][] inputs;
    private final BiConsumer<Object[], Row> accumulator;
    private final Function<Object[], Iterable<Row>> finisher;
    private final Version minNodeVersion;

    public AggregateCollector(List<? extends CollectExpression<Row, ?>> expressions,
                              RamAccounting ramAccounting,
                              MemoryManager memoryManager,
                              Version minNodeVersion,
                              AggregateMode mode,
                              AggregationFunction[] aggregations,
                              Version indexVersionCreated,
                              Input[][] inputs,
                              Input<Boolean>[] filters) {
        this.expressions = expressions;
        this.ramAccounting = ramAccounting;
        this.memoryManager = memoryManager;
        this.minNodeVersion = minNodeVersion;
        this.aggregations = aggregations;
        this.indexVersionCreated = indexVersionCreated;
        this.filters = filters;
        this.inputs = inputs;
        switch (mode) {
            case ITER_PARTIAL:
                accumulator = this::iterate;
                finisher = s -> Collections.singletonList(new RowN(s));
                break;

            case ITER_FINAL:
                accumulator = this::iterate;
                finisher = this::finishCollect;
                break;

            case PARTIAL_FINAL:
                accumulator = this::reduce;
                finisher = this::finishCollect;
                break;

            default:
                throw new AssertionError("Invalid mode: " + mode.name());
        }
    }

    @Override
    public Supplier<Object[]> supplier() {
        return this::prepareState;
    }

    @Override
    public BiConsumer<Object[], Row> accumulator() {
        return accumulator;
    }

    @Override
    public BinaryOperator<Object[]> combiner() {
        return (state1, state2) -> {
            throw new UnsupportedOperationException("combine not supported");
        };
    }

    @Override
    public Function<Object[], Iterable<Row>> finisher() {
        return finisher;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }

    private Object[] prepareState() {
        Object[] states = new Object[aggregations.length];
        for (int i = 0; i < aggregations.length; i++) {
            states[i] = aggregations[i].newState(ramAccounting, indexVersionCreated, minNodeVersion, memoryManager);
        }
        return states;
    }

    private void iterate(Object[] state, Row row) {
        setRow(row);
        for (int i = 0; i < aggregations.length; i++) {
            if (InputCondition.matches(filters[i])) {
                state[i] = aggregations[i].iterate(ramAccounting, memoryManager, state[i], inputs[i]);
            }
        }
    }

    private void reduce(Object[] state, Row row) {
        setRow(row);
        for (int i = 0; i < aggregations.length; i++) {
            state[i] = aggregations[i].reduce(ramAccounting, state[i], inputs[i][0].value());
        }
    }

    private Iterable<Row> finishCollect(Object[] state) {
        for (int i = 0; i < aggregations.length; i++) {
            state[i] = aggregations[i].terminatePartial(ramAccounting, state[i]);
        }
        return Collections.singletonList(new RowN(state));
    }

    private void setRow(Row row) {
        for (int i = 0, expressionsSize = expressions.size(); i < expressionsSize; i++) {
            expressions.get(i).setNextRow(row);
        }
    }
}
