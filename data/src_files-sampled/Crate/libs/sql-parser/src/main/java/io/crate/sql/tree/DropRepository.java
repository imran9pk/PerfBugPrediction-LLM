package io.crate.sql.tree;

import java.util.Objects;

public class DropRepository extends Statement {

    private final String name;

    public DropRepository(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return name.equals(((DropRepository) obj).name);
    }

    @Override
    public String toString() {
        return "DropRepository{repository=" + name + '}';
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitDropRepository(this, context);
    }
}
