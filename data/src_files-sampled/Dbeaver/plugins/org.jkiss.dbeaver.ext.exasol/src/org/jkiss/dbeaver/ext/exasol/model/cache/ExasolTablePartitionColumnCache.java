package org.jkiss.dbeaver.ext.exasol.model.cache;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTable;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTableColumn;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTablePartitionColumn;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.cache.AbstractObjectCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExasolTablePartitionColumnCache extends AbstractObjectCache<ExasolTable, ExasolTablePartitionColumn> {

	
    private List<ExasolTablePartitionColumn> tablePartitionColumns;

    public ExasolTablePartitionColumnCache() {
    	tablePartitionColumns = new ArrayList<ExasolTablePartitionColumn>();
	}
    
	@Override
	public Collection<ExasolTablePartitionColumn> getAllObjects(DBRProgressMonitor monitor, ExasolTable owner)
			throws DBException {
		if (tablePartitionColumns.isEmpty() && ! super.fullCache)
		{
	    	for( ExasolTableColumn col: owner.getAttributes(monitor))
			{
				if (col.getPartitionKeyOrdinalPosition() != null)
				{
					tablePartitionColumns.add(new ExasolTablePartitionColumn(owner, col, col.getPartitionKeyOrdinalPosition().intValue()));
				}
			}
			sortPartitionColumns();
			super.setCache(tablePartitionColumns);
		}
		return tablePartitionColumns;
	}
	
	@Override
	public void clearCache() {
		super.clearCache();
		tablePartitionColumns.clear();
	}

	@Override
	public ExasolTablePartitionColumn getObject(DBRProgressMonitor monitor, ExasolTable owner, String name)
			throws DBException {
		if (!super.isFullyCached())
		{
			getAllObjects(monitor, owner);
		}
		if (tablePartitionColumns.stream()
				.filter(o -> o.getTableColumn().getName().equals(name)).findFirst().isPresent())
		{
			return tablePartitionColumns.stream()
			.filter(o -> o.getName().equals(name)).findFirst().get();
		}
		return null;
	}
	
    private void sortPartitionColumns()
    {
    	tablePartitionColumns = tablePartitionColumns.stream()
    			.sorted(Comparator.comparing(ExasolTablePartitionColumn::getOrdinalPosition))
    			.collect(Collectors.toCollection(ArrayList::new));
    }

	public Collection<ExasolTableColumn> getAvailableTableColumns(ExasolTable owner, DBRProgressMonitor monitor) throws DBException {
		List<ExasolTableColumn> cols = new ArrayList<ExasolTableColumn>();
		
		cols = owner.getAttributes(monitor).stream()
				.filter(c -> ! tablePartitionColumns.stream()
						.filter(pc -> pc.getTableColumn() != null && pc.getName().equals(c.getName()))
						.findFirst().isPresent()
				)
				.filter(c -> c.getDataKind() == DBPDataKind.DATETIME || c.getDataKind() == DBPDataKind.NUMERIC )
				.collect(Collectors.toList());
		
		return cols;
	}


}
