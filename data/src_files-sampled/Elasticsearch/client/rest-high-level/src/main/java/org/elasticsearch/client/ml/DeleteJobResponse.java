package org.elasticsearch.client.ml;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.tasks.TaskId;

import java.io.IOException;
import java.util.Objects;

public class DeleteJobResponse extends ActionResponse implements ToXContentObject {

    private static final ParseField ACKNOWLEDGED = new ParseField("acknowledged");
    private static final ParseField TASK = new ParseField("task");

    public static final ConstructingObjectParser<DeleteJobResponse, Void> PARSER = new ConstructingObjectParser<>("delete_job_response",
            true, a-> new DeleteJobResponse((Boolean) a[0], (TaskId) a[1]));

    static {
        PARSER.declareBoolean(ConstructingObjectParser.optionalConstructorArg(), ACKNOWLEDGED);
        PARSER.declareField(ConstructingObjectParser.optionalConstructorArg(), TaskId.parser(), TASK, ObjectParser.ValueType.STRING);
    }

    public static DeleteJobResponse fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    private final Boolean acknowledged;
    private final TaskId task;

    DeleteJobResponse(@Nullable Boolean acknowledged, @Nullable TaskId task) {
        assert acknowledged != null || task != null;
        this.acknowledged = acknowledged;
        this.task = task;
    }

    public Boolean getAcknowledged() {
        return acknowledged;
    }

    public TaskId getTask() {
        return task;
    }

    @Override
    public int hashCode() {
        return Objects.hash(acknowledged, task);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        DeleteJobResponse that = (DeleteJobResponse) other;
        return Objects.equals(acknowledged, that.acknowledged) && Objects.equals(task, that.task);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (acknowledged != null) {
            builder.field(ACKNOWLEDGED.getPreferredName(), acknowledged);
        }
        if (task != null) {
            builder.field(TASK.getPreferredName(), task.toString());
        }
        builder.endObject();
        return builder;
    }
}
