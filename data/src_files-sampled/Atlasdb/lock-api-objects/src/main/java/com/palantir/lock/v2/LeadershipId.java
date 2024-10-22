package com.palantir.lock.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.palantir.common.annotations.ImmutablesStyles.PackageVisibleImmutablesStyle;
import java.util.UUID;
import org.immutables.value.Value;

@Value.Immutable
@PackageVisibleImmutablesStyle
public abstract class LeadershipId {
    @JsonValue
    @Value.Parameter
    public abstract UUID id();

    @JsonCreator
    static LeadershipId create(UUID uuid) {
        return ImmutableLeadershipId.builder().id(uuid).build();
    }

    public static LeadershipId random() {
        return create(UUID.randomUUID());
    }
}
