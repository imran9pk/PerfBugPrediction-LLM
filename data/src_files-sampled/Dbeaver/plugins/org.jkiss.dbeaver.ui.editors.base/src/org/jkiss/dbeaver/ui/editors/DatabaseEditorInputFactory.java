package org.jkiss.dbeaver.ui.editors;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.CommonUtils;

public class DatabaseEditorInputFactory implements IElementFactory
{
    public static final String ID_FACTORY = DatabaseEditorInputFactory.class.getName(); static final String TAG_CLASS = "class"; static final String TAG_PROJECT = "project"; static final String TAG_DATA_SOURCE = "data-source"; static final String TAG_NODE = "node"; static final String TAG_NODE_NAME = "node-name"; static final String TAG_ACTIVE_PAGE = "page"; static final String TAG_ACTIVE_FOLDER = "folder"; private static volatile boolean lookupEditor;

    public DatabaseEditorInputFactory()
    {
    }

    public static void setLookupEditor(boolean lookupEditor) {
        DatabaseEditorInputFactory.lookupEditor = lookupEditor;
    }

    @Override
    public IAdaptable createElement(IMemento memento) {
        return new DatabaseLazyEditorInput(memento);
    }

    public static void saveState(IMemento memento, DatabaseEditorInput input) {
        if (!DBWorkbench.getPlatform().getPreferenceStore().getBoolean(DatabaseEditorPreferences.PROP_SAVE_EDITORS_STATE)) {
            return;
        }
        final DBCExecutionContext context = input.getExecutionContext();
        if (context == null) {
            return;
        }
        if (input.getDatabaseObject() != null && !input.getDatabaseObject().isPersisted()) {
            return;
        }

        final DBNDatabaseNode node = input.getNavigatorNode();
        memento.putString(TAG_CLASS, input.getClass().getName());
        memento.putString(TAG_PROJECT, context.getDataSource().getContainer().getProject().getName());
        memento.putString(TAG_DATA_SOURCE, context.getDataSource().getContainer().getId());
        memento.putString(TAG_NODE, node.getNodeItemPath());
        memento.putString(TAG_NODE_NAME, node.getNodeName());
        if (!CommonUtils.isEmpty(input.getDefaultPageId())) {
            memento.putString(TAG_ACTIVE_PAGE, input.getDefaultPageId());
        }
        if (!CommonUtils.isEmpty(input.getDefaultFolderId())) {
            memento.putString(TAG_ACTIVE_FOLDER, input.getDefaultFolderId());
        }
    }

}