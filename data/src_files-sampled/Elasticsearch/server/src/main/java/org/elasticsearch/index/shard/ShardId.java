package org.elasticsearch.index.shard;

import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.Index;

import java.io.IOException;

public class ShardId implements Streamable, Comparable<ShardId>, ToXContentFragment {

    private Index index;

    private int shardId;

    private int hashCode;

    private ShardId() {
    }

    public ShardId(Index index, int shardId) {
        this.index = index;
        this.shardId = shardId;
        this.hashCode = computeHashCode();
    }

    public ShardId(String index, String indexUUID, int shardId) {
        this(new Index(index, indexUUID), shardId);
    }

    public Index getIndex() {
        return index;
    }

    public String getIndexName() {
        return index.getName();
    }

    public int id() {
        return this.shardId;
    }

    public int getId() {
        return id();
    }

    @Override
    public String toString() {
        return "[" + index.getName() + "][" + shardId + "]";
    }

    public static ShardId fromString(String shardIdString) {
        int splitPosition = shardIdString.indexOf("][");
        if (splitPosition <= 0 || shardIdString.charAt(0) != '[' || shardIdString.charAt(shardIdString.length() - 1) != ']') {
            throw new IllegalArgumentException("Unexpected shardId string format, expected [indexName][shardId] but got " + shardIdString);
        }
        String indexName = shardIdString.substring(1, splitPosition);
        int shardId = Integer.parseInt(shardIdString.substring(splitPosition + 2, shardIdString.length() - 1));
        return new ShardId(new Index(indexName, IndexMetaData.INDEX_UUID_NA_VALUE), shardId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShardId shardId1 = (ShardId) o;
        return shardId == shardId1.shardId && index.equals(shardId1.index);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = index != null ? index.hashCode() : 0;
        result = 31 * result + shardId;
        return result;
    }

    public static ShardId readShardId(StreamInput in) throws IOException {
        ShardId shardId = new ShardId();
        shardId.readFrom(in);
        return shardId;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        index = new Index(in);
        shardId = in.readVInt();
        hashCode = computeHashCode();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        index.writeTo(out);
        out.writeVInt(shardId);
    }

    @Override
    public int compareTo(ShardId o) {
        if (o.getId() == shardId) {
            int compare = index.getName().compareTo(o.getIndex().getName());
            if (compare != 0) {
                return compare;
            }
            return index.getUUID().compareTo(o.getIndex().getUUID());
        }
        return Integer.compare(shardId, o.getId());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.value(toString());
    }
}
