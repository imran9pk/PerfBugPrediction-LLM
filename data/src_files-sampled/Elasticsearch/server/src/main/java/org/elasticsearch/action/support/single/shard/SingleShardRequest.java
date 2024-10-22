package org.elasticsearch.action.support.single.shard;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.ValidateActions;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;

public abstract class SingleShardRequest<Request extends SingleShardRequest<Request>> extends ActionRequest implements IndicesRequest {

    public static final IndicesOptions INDICES_OPTIONS = IndicesOptions.strictSingleIndexNoExpandForbidClosed();

    @Nullable
    protected String index;
    ShardId internalShardId;
    private boolean threadedOperation = true;

    public SingleShardRequest() {
    }

    protected SingleShardRequest(String index) {
        this.index = index;
    }

    protected ActionRequestValidationException validateNonNullIndex() {
        ActionRequestValidationException validationException = null;
        if (index == null) {
            validationException = ValidateActions.addValidationError("index is missing", validationException);
        }
        return validationException;
    }

    @Nullable
    public String index() {
        return index;
    }

    @SuppressWarnings("unchecked")
    public final Request index(String index) {
        this.index = index;
        return (Request) this;
    }

    @Override
    public String[] indices() {
        return new String[]{index};
    }

    @Override
    public IndicesOptions indicesOptions() {
        return INDICES_OPTIONS;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        if (in.readBoolean()) {
            internalShardId = ShardId.readShardId(in);
        }
        index = in.readOptionalString();
        }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalStreamable(internalShardId);
        out.writeOptionalString(index);
    }

}

