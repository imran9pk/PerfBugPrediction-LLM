package org.elasticsearch.common.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractLifecycleComponent implements LifecycleComponent {
    private static final Logger logger = LogManager.getLogger(AbstractLifecycleComponent.class);

    protected final Lifecycle lifecycle = new Lifecycle();

    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    protected AbstractLifecycleComponent() {}

    @Deprecated
    protected AbstractLifecycleComponent(Settings settings) {
        }

    @Override
    public Lifecycle.State lifecycleState() {
        return this.lifecycle.state();
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void start() {
        if (!lifecycle.canMoveToStarted()) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.beforeStart();
        }
        doStart();
        lifecycle.moveToStarted();
        for (LifecycleListener listener : listeners) {
            listener.afterStart();
        }
    }

    protected abstract void doStart();

    @SuppressWarnings({"unchecked"})
    @Override
    public void stop() {
        if (!lifecycle.canMoveToStopped()) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.beforeStop();
        }
        lifecycle.moveToStopped();
        doStop();
        for (LifecycleListener listener : listeners) {
            listener.afterStop();
        }
    }

    protected abstract void doStop();

    @Override
    public void close() {
        if (lifecycle.started()) {
            stop();
        }
        if (!lifecycle.canMoveToClosed()) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.beforeClose();
        }
        lifecycle.moveToClosed();
        try {
            doClose();
        } catch (IOException e) {
            logger.warn("failed to close " + getClass().getName(), e);
        }
        for (LifecycleListener listener : listeners) {
            listener.afterClose();
        }
    }

    protected abstract void doClose() throws IOException;
}
