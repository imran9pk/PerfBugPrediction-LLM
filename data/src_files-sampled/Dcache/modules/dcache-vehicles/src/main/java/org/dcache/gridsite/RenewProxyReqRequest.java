package org.dcache.gridsite;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;
import javax.security.auth.Subject;

public class RenewProxyReqRequest implements Serializable {

    private static final long serialVersionUID = -2671845960825222398L;
    private final Subject subject;
    private final String delegationID;
    private final Set<Object> publicCredentials;

    public RenewProxyReqRequest(Subject subject, String delegationID) {
        this.subject = subject;
        this.publicCredentials = subject.getPublicCredentials();
        this.delegationID = delegationID;
    }

    public String getDelegationID() {
        return delegationID;
    }

    public Subject getSubject() {
        return subject;
    }

    private void readObject(java.io.ObjectInputStream stream)
          throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        subject.getPublicCredentials().addAll(publicCredentials);
    }
}
