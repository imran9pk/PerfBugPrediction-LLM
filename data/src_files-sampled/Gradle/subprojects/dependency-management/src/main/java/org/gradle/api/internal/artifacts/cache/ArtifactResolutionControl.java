package org.gradle.api.internal.artifacts.cache;

import org.gradle.api.artifacts.ArtifactIdentifier;

import java.io.File;

public interface ArtifactResolutionControl extends ResolutionControl<ArtifactIdentifier, File> {
    boolean belongsToChangingModule();
}
