package org.jkiss.dbeaver.ui.dashboard.view;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;

public class DashboardUpdateJob extends AbstractJob {

    private static final Log log = Log.getLog(DashboardUpdateJob.class);

    private static final int JOB_DELAY = 1000;

    private DashboardUpdateJob() {
        super("Dashboard update");
    }

    @Override
    protected IStatus run(DBRProgressMonitor monitor) {

        try {
            new DashboardUpdater().updateDashboards(monitor);
        } catch (Exception e) {
            log.error("Error running dashboard updater", e);
        }

        if (!DBWorkbench.getPlatform().isShuttingDown()) {
            schedule(JOB_DELAY);
        }
        return Status.OK_STATUS;
    }

    public static void startUpdating() {
        new DashboardUpdateJob().schedule(JOB_DELAY);
    }

}