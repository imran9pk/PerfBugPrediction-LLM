package org.dcache.vehicles.alarms;

import diskCacheV111.vehicles.Message;
import java.util.List;
import org.dcache.alarms.LogEntry;

public class AlarmsDeleteMessage extends Message {

    private List<LogEntry> toDelete;

    public List<LogEntry> getToDelete() {
        return toDelete;
    }

    public void setToDelete(List<LogEntry> toDelete) {
        this.toDelete = toDelete;
    }
}
