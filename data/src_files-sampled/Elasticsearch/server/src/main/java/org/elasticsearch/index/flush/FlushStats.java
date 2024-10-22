package org.elasticsearch.index.flush;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class FlushStats implements Streamable, ToXContentFragment {

    private long total;
    private long periodic;
    private long totalTimeInMillis;

    public FlushStats() {

    }

    public FlushStats(long total, long periodic, long totalTimeInMillis) {
        this.total = total;
        this.periodic = periodic;
        this.totalTimeInMillis = totalTimeInMillis;
    }

    public void add(long total, long periodic, long totalTimeInMillis) {
        this.total += total;
        this.periodic += periodic;
        this.totalTimeInMillis += totalTimeInMillis;
    }

    public void add(FlushStats flushStats) {
        addTotals(flushStats);
    }

    public void addTotals(FlushStats flushStats) {
        if (flushStats == null) {
            return;
        }
        this.total += flushStats.total;
        this.periodic += flushStats.periodic;
        this.totalTimeInMillis += flushStats.totalTimeInMillis;
    }

    public long getTotal() {
        return this.total;
    }

    public long getPeriodic() {
        return periodic;
    }

    public long getTotalTimeInMillis() {
        return this.totalTimeInMillis;
    }

    public TimeValue getTotalTime() {
        return new TimeValue(totalTimeInMillis);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.FLUSH);
        builder.field(Fields.TOTAL, total);
        builder.field(Fields.PERIODIC, periodic);
        builder.humanReadableField(Fields.TOTAL_TIME_IN_MILLIS, Fields.TOTAL_TIME, getTotalTime());
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final String FLUSH = "flush";
        static final String TOTAL = "total";
        static final String PERIODIC = "periodic";
        static final String TOTAL_TIME = "total_time";
        static final String TOTAL_TIME_IN_MILLIS = "total_time_in_millis";
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        total = in.readVLong();
        totalTimeInMillis = in.readVLong();
        if (in.getVersion().onOrAfter(Version.V_6_3_0)) {
            periodic = in.readVLong();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(total);
        out.writeVLong(totalTimeInMillis);
        if (out.getVersion().onOrAfter(Version.V_6_3_0)) {
            out.writeVLong(periodic);
        }
    }
}
