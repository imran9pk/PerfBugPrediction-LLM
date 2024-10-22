package org.gradle.api;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskLocalState;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.TaskState;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public interface Task extends Comparable<Task>, ExtensionAware {
    String TASK_NAME = "name";

    String TASK_DESCRIPTION = "description";

    String TASK_GROUP = "group";

    String TASK_TYPE = "type";

    String TASK_DEPENDS_ON = "dependsOn";

    String TASK_OVERWRITE = "overwrite";

    String TASK_ACTION = "action";

    String TASK_CONSTRUCTOR_ARGS = "constructorArgs";

    @Internal
    String getName();

    class Namer implements org.gradle.api.Namer<Task> {
        @Override
        public String determineName(Task c) {
            return c.getName();
        }
    }

    @Internal
    Project getProject();

    @Internal
    List<Action<? super Task>> getActions();

    void setActions(List<Action<? super Task>> actions);

    @Internal
    TaskDependency getTaskDependencies();

    @Internal
    Set<Object> getDependsOn();

    void setDependsOn(Iterable<?> dependsOnTasks);

    Task dependsOn(Object... paths);

    void onlyIf(Closure onlyIfClosure);

    @Incubating
    @Internal
    void doNotTrackState(String reasonNotToTrackState);

    void onlyIf(Spec<? super Task> onlyIfSpec);

    void setOnlyIf(Closure onlyIfClosure);

    void setOnlyIf(Spec<? super Task> onlyIfSpec);

    @Internal
    TaskState getState();

    void setDidWork(boolean didWork);

    @Internal
    boolean getDidWork();

    @Internal
    String getPath();

    Task doFirst(Action<? super Task> action);

    Task doFirst(Closure action);

    Task doFirst(String actionName, Action<? super Task> action);

    Task doLast(Action<? super Task> action);

    Task doLast(String actionName, Action<? super Task> action);

    Task doLast(Closure action);

    @Internal
    boolean getEnabled();

    void setEnabled(boolean enabled);

    Task configure(Closure configureClosure);

    @Internal
    AntBuilder getAnt();

    @Internal
    Logger getLogger();

    @Internal
    LoggingManager getLogging();

    @Nullable
    Object property(String propertyName) throws MissingPropertyException;

    boolean hasProperty(String propertyName);

    void setProperty(String name, Object value) throws MissingPropertyException;

    @Internal
    @Deprecated
    Convention getConvention();

    @Internal
    @Nullable
    String getDescription();

    void setDescription(@Nullable String description);

    @Internal
    @Nullable
    String getGroup();

    void setGroup(@Nullable String group);

    @Internal
    TaskInputs getInputs();

    @Internal
    TaskOutputs getOutputs();

    @Internal
    TaskDestroyables getDestroyables();

    @Internal
    TaskLocalState getLocalState();

    @Internal
    File getTemporaryDir();

    Task mustRunAfter(Object... paths);

    void setMustRunAfter(Iterable<?> mustRunAfter);

    @Internal
    TaskDependency getMustRunAfter();

    Task finalizedBy(Object... paths);

    void setFinalizedBy(Iterable<?> finalizedBy);

    @Internal
    TaskDependency getFinalizedBy();

    TaskDependency shouldRunAfter(Object... paths);

    void setShouldRunAfter(Iterable<?> shouldRunAfter);

    @Internal
    TaskDependency getShouldRunAfter();

    @Internal
    @Optional
    Property<Duration> getTimeout();

    @Incubating
    void usesService(Provider<? extends BuildService<?>> service);
}
