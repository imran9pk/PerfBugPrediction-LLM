package io.crate.expression.symbol;

import io.crate.expression.symbol.format.Style;
import io.crate.metadata.Reference;
import io.crate.metadata.RelationName;
import io.crate.metadata.doc.DocSysColumns;
import io.crate.types.DataType;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.List;

public final class FetchMarker extends Symbol {

    private final RelationName relationName;
    private final List<Reference> fetchRefs;
    private final Reference fetchId;

    public FetchMarker(RelationName relationName, List<Reference> fetchRefs) {
        this(relationName, fetchRefs, DocSysColumns.forTable(relationName, DocSysColumns.FETCHID));
    }

    public FetchMarker(RelationName relationName, List<Reference> fetchRefs, Reference fetchId) {
        this.relationName = relationName;
        this.fetchRefs = fetchRefs;
        this.fetchId = fetchId;
    }

    public List<Reference> fetchRefs() {
        return fetchRefs;
    }

    public RelationName relationName() {
        return relationName;
    }

    public Reference fetchId() {
        return fetchId;
    }

    @Override
    public SymbolType symbolType() {
        return SymbolType.REFERENCE;
    }

    @Override
    public <C, R> R accept(SymbolVisitor<C, R> visitor, C context) {
        return visitor.visitFetchMarker(this, context);
    }

    @Override
    public DataType<?> valueType() {
        return fetchId.valueType();
    }

    @Override
    public String toString(Style style) {
        if (relationName.schema() == null) {
            return relationName + "." + fetchId.toString(style);
        }
        return fetchId.toString(style);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        fetchId.writeTo(out);
    }
}
