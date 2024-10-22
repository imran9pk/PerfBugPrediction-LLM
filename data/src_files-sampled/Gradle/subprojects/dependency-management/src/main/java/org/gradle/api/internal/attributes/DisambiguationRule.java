package org.gradle.api.internal.attributes;

import org.gradle.api.Action;

public interface DisambiguationRule<T> extends Action<MultipleCandidatesResult<T>> {
    boolean doesSomething();
}
