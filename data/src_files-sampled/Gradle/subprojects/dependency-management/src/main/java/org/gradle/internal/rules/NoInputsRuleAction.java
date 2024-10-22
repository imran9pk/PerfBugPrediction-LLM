package org.gradle.internal.rules;

import org.gradle.api.Action;

import java.util.Collections;
import java.util.List;

public class NoInputsRuleAction<T> implements RuleAction<T> {
    private final Action<? super T> action;

    public NoInputsRuleAction(Action<? super T> action) {
        this.action = action;
    }

    @Override
    public List<Class<?>> getInputTypes() {
        return Collections.emptyList();
    }

    @Override
    public void execute(T subject, List<?> inputs) {
        action.execute(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NoInputsRuleAction<?> that = (NoInputsRuleAction<?>) o;
        return action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return action.hashCode();
    }
}
