package org.dcache.services.bulk;

import org.dcache.auth.attributes.Restriction;

public class BulkRequestClearMessage extends BulkServiceMessage {

    private static final long serialVersionUID = 957050116233062328L;
    private final String requestId;

    public BulkRequestClearMessage(String requestId, Restriction restriction) {
        super(restriction);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
