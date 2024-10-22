package org.gradle.internal.component.model;

import javax.annotation.Nullable;

public interface IvyArtifactName {
    String getName();

    String getType();

    @Nullable
    String getExtension();

    @Nullable
    String getClassifier();
}
