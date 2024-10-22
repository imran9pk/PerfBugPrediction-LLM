package org.elasticsearch.action.support.replication;

import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;


public class ReplicationTask extends Task {
    private volatile String phase = "starting";

    public ReplicationTask(long id, String type, String action, String description, TaskId parentTaskId) {
        super(id, type, action, description, parentTaskId);
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getPhase() {
        return phase;
    }
}
