package org.jkiss.dbeaver.ui.dashboard.view;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.jkiss.dbeaver.ui.dashboard.model.DashboardContainer;

public class HandlerDashboardRemoveItem extends HandlerDashboardAbstract {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        DashboardView view = getActiveDashboardView(event);
        if (view != null) {
            DashboardContainer selectedDashboard = getSelectedDashboard(view);
            if (selectedDashboard != null) {
                selectedDashboard.getGroup().removeItem(selectedDashboard);
            }

        }
        return null;
    }

}