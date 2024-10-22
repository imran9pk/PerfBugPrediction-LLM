package org.jkiss.dbeaver.ui.data.editors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ui.data.IValueController;

public class PagedPanelEditor extends BaseValueEditor<Composite> {
    public PagedPanelEditor(IValueController controller) {
        super(controller);
    }

    @Override
    public Object extractEditorValue()
    {
        return null;
    }

    @Override
    public void primeEditorValue(@Nullable Object value) throws DBException
    {
    }

    @Override
    protected Composite createControl(Composite editPlaceholder)
    {
        return new Composite(editPlaceholder, SWT.NONE);
    }
}
