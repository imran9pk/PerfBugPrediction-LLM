package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.RejectedVersion;

import java.util.Collection;

public interface BuildableComponentIdResolveResult extends ComponentIdResolveResult, ResourceAwareResolveResult {
    void resolved(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier);

    void rejected(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier);

    void resolved(ComponentResolveMetadata metaData);

    void failed(ModuleVersionResolveException failure);

    void unmatched(Collection<String> unmatchedVersions);

    void rejections(Collection<RejectedVersion> rejections);
}
