package org.elasticsearch.transport;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class ActionNotFoundTransportException extends TransportException {

    private final String action;

    public ActionNotFoundTransportException(StreamInput in) throws IOException {
        super(in);
        action = in.readOptionalString();
    }

    public ActionNotFoundTransportException(String action) {
        super("No handler for action [" + action + "]");
        this.action = action;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(action);
    }

    public String action() {
        return this.action;
    }
}
