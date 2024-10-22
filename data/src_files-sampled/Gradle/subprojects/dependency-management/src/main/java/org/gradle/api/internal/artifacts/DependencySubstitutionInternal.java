package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ArtifactSelectionDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;

import java.util.List;

public interface DependencySubstitutionInternal extends DependencySubstitution {
    void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor);

    ComponentSelector getTarget();

    List<ComponentSelectionDescriptorInternal> getRuleDescriptors();

    boolean isUpdated();

    ArtifactSelectionDetailsInternal getArtifactSelectionDetails();
}
