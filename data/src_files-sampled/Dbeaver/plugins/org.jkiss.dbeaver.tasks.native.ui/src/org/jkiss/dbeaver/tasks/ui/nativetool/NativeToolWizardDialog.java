package org.jkiss.dbeaver.tasks.ui.nativetool;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.tasks.ui.nativetool.internal.TaskNativeUIMessages;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizard;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizardDialog;
import org.jkiss.dbeaver.ui.dialogs.BaseDialog;
import org.jkiss.dbeaver.ui.dialogs.connection.ClientHomesSelector;
import org.jkiss.dbeaver.ui.internal.UIMessages;

public class NativeToolWizardDialog extends TaskConfigurationWizardDialog {

    public static final int CLIENT_CONFIG_ID = 1000;

    public NativeToolWizardDialog(IWorkbenchWindow window, TaskConfigurationWizard wizard) {
        super(window, wizard);
        setShellStyle(SWT.CLOSE | SWT.MAX | SWT.MIN | SWT.TITLE | SWT.BORDER | SWT.RESIZE | getDefaultOrientation());
        setHelpAvailable(false);
        setFinishButtonLabel(UIMessages.button_start);
    }

    protected IDialogSettings getDialogBoundsSettings() {
        return null;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        if (getWizard() instanceof AbstractNativeToolWizard<?, ?, ?>) {
            boolean nativeClientRequired = ((AbstractNativeToolWizard) getWizard()).isNativeClientHomeRequired();
            if (nativeClientRequired) {
                parent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

                Button configButton = createButton(parent, CLIENT_CONFIG_ID, TaskNativeUIMessages.tools_wizard_client_button, false);
                Label spacer = new Label(parent, SWT.NONE);
                spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

                ((GridLayout) parent.getLayout()).numColumns++;
                ((GridLayout) parent.getLayout()).makeColumnsEqualWidth = false;
            }
        }

        super.createButtonsForButtonBar(parent);
    }

    @Override
    public void disableButtonsOnProgress() {
        Button button = getButton(CLIENT_CONFIG_ID);
        if (button != null) {
            button.setEnabled(false);
        }
        super.disableButtonsOnProgress();
    }

    @Override
    public void enableButtonsAfterProgress() {
        Button button = getButton(CLIENT_CONFIG_ID);
        if (button != null) {
            button.setEnabled(true);
        }
        super.enableButtonsAfterProgress();
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == CLIENT_CONFIG_ID) {
            openClientConfiguration();
        }
        super.buttonPressed(buttonId);
    }

    private void openClientConfiguration() {
        AbstractNativeToolWizard<?,?,?> toolWizard = (AbstractNativeToolWizard) getWizard();
        DBPDataSourceContainer dataSource = toolWizard.getSettings().getDataSourceContainer();
        if (dataSource != null) {
            NativeClientConfigDialog dialog = new NativeClientConfigDialog(getShell(), dataSource);
            if (dialog.open() == IDialogConstants.OK_ID) {
                if (toolWizard instanceof AbstractNativeToolWizard) {
                    toolWizard.readLocalClientInfo();
                }
                updateButtons();

                updatePageCompletion();
            }
        }
    }

    private static class NativeClientConfigDialog extends BaseDialog {
        private final DBPDataSourceContainer dataSource;
        private ClientHomesSelector homesSelector;

        public NativeClientConfigDialog(Shell parentShell, DBPDataSourceContainer dataSource) {
            super(parentShell, NLS.bind(TaskNativeUIMessages.tools_wizard_client_dialog_title, dataSource.getName()), dataSource.getDriver().getIcon());
            this.dataSource = dataSource;
        }

        @Override
        protected Composite createDialogArea(Composite parent) {
            Composite dialogArea = super.createDialogArea(parent);

            homesSelector = new ClientHomesSelector(dialogArea, TaskNativeUIMessages.tools_wizard_client_group_client);
            homesSelector.populateHomes(dataSource.getDriver(), dataSource.getConnectionConfiguration().getClientHomeId(), true);
            homesSelector.getPanel().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

            return dialogArea;
        }

        @Override
        protected void okPressed() {
            String selectedHome = homesSelector.getSelectedHome();
            dataSource.getConnectionConfiguration().setClientHomeId(selectedHome);
            dataSource.persistConfiguration();
            super.okPressed();
        }
    }
}
