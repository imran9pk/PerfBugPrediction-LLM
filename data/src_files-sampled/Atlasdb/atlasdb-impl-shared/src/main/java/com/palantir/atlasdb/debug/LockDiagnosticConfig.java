package com.palantir.atlasdb.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Duration;
import org.immutables.value.Value;

@Deprecated
@Value.Immutable
@JsonDeserialize(as = ImmutableLockDiagnosticConfig.class)
@JsonSerialize(as = ImmutableLockDiagnosticConfig.class)
public interface LockDiagnosticConfig {

    @JsonProperty("maximum-size")
    long maximumSize();

    Duration ttl();
}
