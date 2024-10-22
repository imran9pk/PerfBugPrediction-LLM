package org.jkiss.dbeaver.ext.oracle.model;

import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableForeignKeyColumn;

import java.util.List;

public class OracleTableForeignKeyColumn extends OracleTableConstraintColumn implements DBSTableForeignKeyColumn
{

    public OracleTableForeignKeyColumn(
        OracleTableForeignKey constraint,
        OracleTableColumn tableColumn,
        int ordinalPosition)
    {
        super(constraint, tableColumn, ordinalPosition);
    }

    @Override
    @Property(id = "reference", viewable = true, order = 4)
    public OracleTableColumn getReferencedColumn()
    {
        OracleTableConstraint referencedConstraint = ((OracleTableForeignKey) getParentObject()).getReferencedConstraint();
        if (referencedConstraint != null) {
            List<OracleTableConstraintColumn> ar = referencedConstraint.getAttributeReferences(new VoidProgressMonitor());
            if (ar != null) {
                return ar.get(getOrdinalPosition() - 1).getAttribute();
            }
        }
        return null;
    }

}
