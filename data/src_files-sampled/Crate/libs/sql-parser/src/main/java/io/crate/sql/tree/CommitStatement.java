package io.crate.sql.tree;

public class CommitStatement extends Statement {

    public CommitStatement() {
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "COMMIT";
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitCommit(this, context);
    }
}
