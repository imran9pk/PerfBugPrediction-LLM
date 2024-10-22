package org.elasticsearch.cluster;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;

import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

public class ClusterInfo implements ToXContentFragment, Writeable {

    private final ImmutableOpenMap<String, DiskUsage> leastAvailableSpaceUsage;
    private final ImmutableOpenMap<String, DiskUsage> mostAvailableSpaceUsage;
    final ImmutableOpenMap<String, Long> shardSizes;
    public static final ClusterInfo EMPTY = new ClusterInfo();
    final ImmutableOpenMap<ShardRouting, String> routingToDataPath;

    protected ClusterInfo() {
        this(ImmutableOpenMap.of(), ImmutableOpenMap.of(), ImmutableOpenMap.of(), ImmutableOpenMap.of());
    }

    public ClusterInfo(ImmutableOpenMap<String, DiskUsage> leastAvailableSpaceUsage,
            ImmutableOpenMap<String, DiskUsage> mostAvailableSpaceUsage, ImmutableOpenMap<String, Long> shardSizes,
            ImmutableOpenMap<ShardRouting, String> routingToDataPath) {
        this.leastAvailableSpaceUsage = leastAvailableSpaceUsage;
        this.shardSizes = shardSizes;
        this.mostAvailableSpaceUsage = mostAvailableSpaceUsage;
        this.routingToDataPath = routingToDataPath;
    }

    public ClusterInfo(StreamInput in) throws IOException {
        Map<String, DiskUsage> leastMap = in.readMap(StreamInput::readString, DiskUsage::new);
        Map<String, DiskUsage> mostMap = in.readMap(StreamInput::readString, DiskUsage::new);
        Map<String, Long> sizeMap = in.readMap(StreamInput::readString, StreamInput::readLong);
        final Map<ShardRouting, String> routingMap = in.readMap(ShardRouting::new, StreamInput::readString);

        ImmutableOpenMap.Builder<String, DiskUsage> leastBuilder = ImmutableOpenMap.builder();
        this.leastAvailableSpaceUsage = leastBuilder.putAll(leastMap).build();
        ImmutableOpenMap.Builder<String, DiskUsage> mostBuilder = ImmutableOpenMap.builder();
        this.mostAvailableSpaceUsage = mostBuilder.putAll(mostMap).build();
        ImmutableOpenMap.Builder<String, Long> sizeBuilder = ImmutableOpenMap.builder();
        this.shardSizes = sizeBuilder.putAll(sizeMap).build();
        ImmutableOpenMap.Builder<ShardRouting, String> routingBuilder = ImmutableOpenMap.builder();
        this.routingToDataPath = routingBuilder.putAll(routingMap).build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(this.leastAvailableSpaceUsage.size());
        for (ObjectObjectCursor<String, DiskUsage> c : this.leastAvailableSpaceUsage) {
            out.writeString(c.key);
            c.value.writeTo(out);
        }
        out.writeVInt(this.mostAvailableSpaceUsage.size());
        for (ObjectObjectCursor<String, DiskUsage> c : this.mostAvailableSpaceUsage) {
            out.writeString(c.key);
            c.value.writeTo(out);
        }
        out.writeVInt(this.shardSizes.size());
        for (ObjectObjectCursor<String, Long> c : this.shardSizes) {
            out.writeString(c.key);
            if (c.value == null) {
                out.writeLong(-1);
            } else {
                out.writeLong(c.value);
            }
        }
        out.writeVInt(this.routingToDataPath.size());
        for (ObjectObjectCursor<ShardRouting, String> c : this.routingToDataPath) {
            c.key.writeTo(out);
            out.writeString(c.value);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("nodes"); {
            for (ObjectObjectCursor<String, DiskUsage> c : this.leastAvailableSpaceUsage) {
                builder.startObject(c.key); { builder.field("node_name", c.value.getNodeName());
                    builder.startObject("least_available"); {
                        c.value.toShortXContent(builder);
                    }
                    builder.endObject(); builder.startObject("most_available"); {
                        DiskUsage most = this.mostAvailableSpaceUsage.get(c.key);
                        if (most != null) {
                            most.toShortXContent(builder);
                        }
                    }
                    builder.endObject(); }
                builder.endObject(); }
        }
        builder.endObject(); builder.startObject("shard_sizes"); {
            for (ObjectObjectCursor<String, Long> c : this.shardSizes) {
                builder.humanReadableField(c.key + "_bytes", c.key, new ByteSizeValue(c.value));
            }
        }
        builder.endObject(); builder.startObject("shard_paths"); {
            for (ObjectObjectCursor<ShardRouting, String> c : this.routingToDataPath) {
                builder.field(c.key.toString(), c.value);
            }
        }
        builder.endObject(); return builder;
    }

    public ImmutableOpenMap<String, DiskUsage> getNodeLeastAvailableDiskUsages() {
        return this.leastAvailableSpaceUsage;
    }

    public ImmutableOpenMap<String, DiskUsage> getNodeMostAvailableDiskUsages() {
        return this.mostAvailableSpaceUsage;
    }

    public Long getShardSize(ShardRouting shardRouting) {
        return shardSizes.get(shardIdentifierFromRouting(shardRouting));
    }

    public String getDataPath(ShardRouting shardRouting) {
        return routingToDataPath.get(shardRouting);
    }

    public long getShardSize(ShardRouting shardRouting, long defaultValue) {
        Long shardSize = getShardSize(shardRouting);
        return shardSize == null ? defaultValue : shardSize;
    }

    static String shardIdentifierFromRouting(ShardRouting shardRouting) {
        return shardRouting.shardId().toString() + "[" + (shardRouting.primary() ? "p" : "r") + "]";
    }
}
