package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DirectDependencyMetadata;

import java.util.Collections;
import java.util.List;

public class DirectDependencyMetadataImpl extends AbstractDependencyImpl<DirectDependencyMetadata> implements DirectDependencyMetadata {

    private boolean endorsing = false;

    public DirectDependencyMetadataImpl(String group, String name, String version) {
        super(group, name, version);
    }

    @Override
    public void endorseStrictVersions() {
        endorsing = true;
    }

    @Override
    public void doNotEndorseStrictVersions() {
        endorsing = false;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return endorsing;
    }

    @Override
    public List<DependencyArtifact> getArtifactSelectors() {
        return Collections.emptyList();
    }

}
