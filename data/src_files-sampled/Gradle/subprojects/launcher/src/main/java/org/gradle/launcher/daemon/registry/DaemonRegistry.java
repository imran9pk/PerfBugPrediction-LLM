package org.gradle.launcher.daemon.registry;

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.internal.remote.Address;

import java.util.Collection;
import java.util.List;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.*;

@ThreadSafe
public interface DaemonRegistry {

    List<DaemonInfo> getAll();
    List<DaemonInfo> getIdle();
    List<DaemonInfo> getNotIdle();
    List<DaemonInfo> getCanceled();

    void store(DaemonInfo info);
    void remove(Address address);
    void markState(Address address, State state);

    void storeStopEvent(DaemonStopEvent stopEvent);
    List<DaemonStopEvent> getStopEvents();
    void removeStopEvents(Collection<DaemonStopEvent> stopEvents);

    static class EmptyRegistryException extends RuntimeException {
        public EmptyRegistryException(String message) {
            super(message);
        }
    }
}
