package io.crate.sql.tree;

public class LongLiteral extends Literal {

    private final long value;

    public LongLiteral(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitLongLiteral(this, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LongLiteral that = (LongLiteral) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }
}
