package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.MultipleCandidatesDetails;

import java.util.Set;

public interface MultipleCandidatesResult<T> extends MultipleCandidatesDetails<T> {
    boolean hasResult();

    Set<T> getMatches();
}
