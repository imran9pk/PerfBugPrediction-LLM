package io.crate.execution.engine.fetch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntContainer;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import javax.annotation.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.transport.TransportRequest;

import java.io.IOException;
import java.util.UUID;

public class NodeFetchRequest extends TransportRequest {

    private final UUID jobId;
    private final int fetchPhaseId;
    private final boolean closeContext;

    @Nullable
    private final IntObjectMap<IntArrayList> toFetch;

    public NodeFetchRequest(UUID jobId,
                            int fetchPhaseId,
                            boolean closeContext,
                            IntObjectMap<IntArrayList> toFetch) {
        this.jobId = jobId;
        this.fetchPhaseId = fetchPhaseId;
        this.closeContext = closeContext;
        if (toFetch.isEmpty()) {
            this.toFetch = null;
        } else {
            this.toFetch = toFetch;
        }
    }

    public UUID jobId() {
        return jobId;
    }

    public int fetchPhaseId() {
        return fetchPhaseId;
    }

    public boolean isCloseContext() {
        return closeContext;
    }

    @Nullable
    public IntObjectMap<IntArrayList> toFetch() {
        return toFetch;
    }

    public NodeFetchRequest(StreamInput in) throws IOException {
        super(in);
        jobId = new UUID(in.readLong(), in.readLong());
        fetchPhaseId = in.readVInt();
        closeContext = in.readBoolean();
        int numReaders = in.readVInt();
        if (numReaders > 0) {
            IntObjectHashMap<IntArrayList> toFetch = new IntObjectHashMap<>(numReaders);
            for (int i = 0; i < numReaders; i++) {
                int readerId = in.readVInt();
                int numDocs = in.readVInt();
                IntArrayList docs = new IntArrayList(numDocs);
                toFetch.put(readerId, docs);
                for (int j = 0; j < numDocs; j++) {
                    docs.add(in.readInt());
                }
            }
            this.toFetch = toFetch;
        } else {
            this.toFetch = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeLong(jobId.getMostSignificantBits());
        out.writeLong(jobId.getLeastSignificantBits());
        out.writeVInt(fetchPhaseId);
        out.writeBoolean(closeContext);
        if (toFetch == null) {
            out.writeVInt(0);
        } else {
            out.writeVInt(toFetch.size());
            for (IntObjectCursor<? extends IntContainer> toFetchCursor : toFetch) {
                out.writeVInt(toFetchCursor.key);
                out.writeVInt(toFetchCursor.value.size());
                for (IntCursor docCursor : toFetchCursor.value) {
                    out.writeInt(docCursor.value);
                }
            }
        }
    }
}
