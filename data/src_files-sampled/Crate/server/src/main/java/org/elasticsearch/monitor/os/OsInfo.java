package org.elasticsearch.monitor.os;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

import java.io.IOException;

public class OsInfo implements Writeable {

    private final long refreshInterval;
    private final int availableProcessors;
    private final int allocatedProcessors;
    private final String name;
    private final String arch;
    private final String version;

    public OsInfo(long refreshInterval, int availableProcessors, int allocatedProcessors, String name, String arch, String version) {
        this.refreshInterval = refreshInterval;
        this.availableProcessors = availableProcessors;
        this.allocatedProcessors = allocatedProcessors;
        this.name = name;
        this.arch = arch;
        this.version = version;
    }

    public OsInfo(StreamInput in) throws IOException {
        this.refreshInterval = in.readLong();
        this.availableProcessors = in.readInt();
        this.allocatedProcessors = in.readInt();
        this.name = in.readOptionalString();
        this.arch = in.readOptionalString();
        this.version = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(refreshInterval);
        out.writeInt(availableProcessors);
        out.writeInt(allocatedProcessors);
        out.writeOptionalString(name);
        out.writeOptionalString(arch);
        out.writeOptionalString(version);
    }

    public long getRefreshInterval() {
        return this.refreshInterval;
    }

    public int getAvailableProcessors() {
        return this.availableProcessors;
    }

    public int getAllocatedProcessors() {
        return this.allocatedProcessors;
    }

    public String getName() {
        return name;
    }

    public String getArch() {
        return arch;
    }

    public String getVersion() {
        return version;
    }
}
