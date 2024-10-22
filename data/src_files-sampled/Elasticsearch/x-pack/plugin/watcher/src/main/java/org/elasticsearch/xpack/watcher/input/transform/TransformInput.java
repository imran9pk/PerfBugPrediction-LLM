package org.elasticsearch.xpack.watcher.input.transform;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.watcher.input.Input;
import org.elasticsearch.xpack.core.watcher.transform.Transform;
import org.elasticsearch.xpack.core.watcher.watch.Payload;

import java.io.IOException;
import java.util.Objects;

public class TransformInput implements Input {

    public static final String TYPE = "transform";

    private final Transform transform;

    public TransformInput(Transform transform) {
        this.transform = transform;
    }

    public Transform getTransform() {
        return transform;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject().field(transform.type(), transform, params).endObject();
        return builder;
    }

    @Override
    public int hashCode() {
        return transform.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformInput that = (TransformInput) o;

        return Objects.equals(transform, that.transform);
    }

    static class Result extends Input.Result {

        Result(Payload payload) {
            super(TYPE, payload);
        }

        @Override
        protected XContentBuilder typeXContent(XContentBuilder builder, Params params) throws IOException {
            return builder;
        }
    }
}