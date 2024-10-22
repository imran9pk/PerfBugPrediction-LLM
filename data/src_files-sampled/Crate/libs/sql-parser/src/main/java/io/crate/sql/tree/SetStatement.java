package io.crate.sql.tree;

import io.crate.common.collections.Lists2;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class SetStatement<T> extends Statement {

    public enum Scope {
        GLOBAL, SESSION, LOCAL, LICENSE
    }

    public enum SettingType {
        TRANSIENT, PERSISTENT
    }

    private final Scope scope;
    private final SettingType settingType;
    private final List<Assignment<T>> assignments;

    public SetStatement(Scope scope, List<Assignment<T>> assignments) {
        this(scope, SettingType.TRANSIENT, assignments);
    }

    public SetStatement(Scope scope, SettingType settingType, List<Assignment<T>> assignments) {
        this.scope = scope;
        this.settingType = settingType;
        this.assignments = assignments;
    }

    public SetStatement(Scope scope, Assignment<T> assignment) {
        this.scope = scope;
        this.settingType = SettingType.TRANSIENT;
        this.assignments = Collections.singletonList(assignment);
    }

    public Scope scope() {
        return scope;
    }

    public List<Assignment<T>> assignments() {
        return assignments;
    }

    public SettingType settingType() {
        return settingType;
    }


    public <U> SetStatement<U> map(Function<? super T, ? extends U> mapper) {
        return new SetStatement<>(
            scope,
            settingType,
            Lists2.map(assignments, x -> x.map(mapper))
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SetStatement<?> that = (SetStatement<?>) o;
        return scope == that.scope &&
               settingType == that.settingType &&
               Objects.equals(assignments, that.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, settingType, assignments);
    }

    @Override
    public String toString() {
        return "SetStatement{" +
               "scope=" + scope +
               ", assignments=" + assignments +
               ", settingType=" + settingType +
               '}';
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitSetStatement(this, context);
    }
}
