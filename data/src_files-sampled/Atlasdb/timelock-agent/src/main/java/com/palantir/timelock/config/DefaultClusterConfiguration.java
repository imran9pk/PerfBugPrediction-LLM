package com.palantir.timelock.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableDefaultClusterConfiguration.class)
@JsonDeserialize(as = ImmutableDefaultClusterConfiguration.class)
public interface DefaultClusterConfiguration extends ClusterConfiguration {

    String TYPE = "default";

    @Override
    default List<String> clusterMembers() {
        return cluster().uris();
    }
}
