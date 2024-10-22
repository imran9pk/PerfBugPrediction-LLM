package com.codename1.ui.table;

import com.codename1.ui.events.DataChangedListener;
import com.codename1.ui.util.EventDispatcher;
import java.util.ArrayList;

public class DefaultTableModel extends AbstractTableModel {
    ArrayList<Object[]> data = new ArrayList<Object[]>();
    String[] columnNames;
    private EventDispatcher dispatcher = new EventDispatcher();
    boolean editable;

    public DefaultTableModel(String[] columnNames, Object[][] data) {
        this(columnNames, data, false);
    }

    public DefaultTableModel(String[] columnNames, Object[][] data, boolean editable) {
        for(Object[] o : data) {
            this.data.add(o);
        }
        this.columnNames = columnNames;
        this.editable = editable;
    }

    DefaultTableModel(String[] columnNames, ArrayList<Object[]> data, boolean editable) {
        this.data = data;
        this.columnNames = columnNames;
        this.editable = editable;
    }

    public int getRowCount() {
        return data.size();
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public String getColumnName(int i) {
        return columnNames[i];
    }

    public boolean isCellEditable(int row, int column) {
        return editable;
    }

    public Object getValueAt(int row, int column) {
        try {
            return data.get(row)[column];
        } catch(ArrayIndexOutOfBoundsException err) {
            return "";
        }
    }

    public void setValueAt(int row, int column, Object o) {
        data.get(row)[column] = o;
        dispatcher.fireDataChangeEvent(column, row);
    }

    public void addDataChangeListener(DataChangedListener d) {
        dispatcher.addListener(d);
    }

    public void removeDataChangeListener(DataChangedListener d) {
        dispatcher.removeListener(d);
    }

    public void addRow(Object... row) {
       data.add(row);
       for(int col = 0 ; col < row.length ; col++) {
           dispatcher.fireDataChangeEvent(col, data.size() - 1);
       }
    }

    public void insertRow(int offset, Object... row) {
       data.add(offset, row);
       for(int col = 0 ; col < row.length ; col++) {
           dispatcher.fireDataChangeEvent(col, data.size() - 1);
           dispatcher.fireDataChangeEvent(col, offset);
       }
    }

    public void removeRow(int offset) {
       data.remove(offset);
       dispatcher.fireDataChangeEvent(Integer.MIN_VALUE, Integer.MIN_VALUE);
    }
}
