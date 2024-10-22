package org.gradle.api.tasks.diagnostics.internal;

import java.util.Set;

public interface TaskReportModel {
    String DEFAULT_GROUP = "";

    Set<String> getGroups();

    Set<TaskDetails> getTasksForGroup(String group);
}
