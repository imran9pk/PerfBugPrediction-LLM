package org.jkiss.dbeaver.ui.data.editors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.List;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ui.data.IValueController;

public class BooleanPanelEditor extends BaseValueEditor<List> {
    public BooleanPanelEditor(IValueController controller) {
        super(controller);
    }

    @Override
    public Object extractEditorValue()
    {
        return control.getSelectionIndex() == 1;
    }

    @Override
    public void primeEditorValue(@Nullable Object value) throws DBException
    {
        control.setSelection(Boolean.TRUE.equals(value) ? 1 : 0);
    }

    @Override
    protected List createControl(Composite editPlaceholder)
    {
        final List editor = new List(valueController.getEditPlaceholder(), SWT.SINGLE | SWT.READ_ONLY);
        editor.add("FALSE");
        editor.add("TRUE");
        return editor;
    }
}
