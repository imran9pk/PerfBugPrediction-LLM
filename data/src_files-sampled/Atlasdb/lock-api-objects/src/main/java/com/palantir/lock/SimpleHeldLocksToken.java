package com.palantir.lock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.logsafe.Preconditions;
import java.io.Serializable;
import java.math.BigInteger;

public class SimpleHeldLocksToken implements Serializable {
    private static final long serialVersionUID = 1L;
    private final BigInteger tokenId;
    private final long creationDateMs;

    public SimpleHeldLocksToken(
            @JsonProperty("tokenId") BigInteger tokenId, @JsonProperty("creationDateMs") long creationDateMs) {
        this.tokenId = Preconditions.checkNotNull(tokenId, "tokenId should not be null");
        this.creationDateMs = creationDateMs;
    }

    public static SimpleHeldLocksToken fromHeldLocksToken(HeldLocksToken token) {
        return new SimpleHeldLocksToken(token.getTokenId(), token.getCreationDateMs());
    }

    public static SimpleHeldLocksToken fromLockRefreshToken(LockRefreshToken token) {
        return new SimpleHeldLocksToken(token.getTokenId(), 0L);
    }

    public LockRefreshToken asLockRefreshToken() {
        return new LockRefreshToken(tokenId, 0L);
    }

    public BigInteger getTokenId() {
        return tokenId;
    }

    public long getCreationDateMs() {
        return creationDateMs;
    }

    @Override
    public String toString() {
        return "SimpleHeldLocksToken [tokenId=" + tokenId + ", creationDateMs=" + creationDateMs + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tokenId == null) ? 0 : tokenId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SimpleHeldLocksToken other = (SimpleHeldLocksToken) obj;
        if (tokenId == null) {
            if (other.tokenId != null) {
                return false;
            }
        } else if (!tokenId.equals(other.tokenId)) {
            return false;
        }
        return true;
    }
}
