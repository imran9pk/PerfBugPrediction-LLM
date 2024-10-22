package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;

public interface VisitedArtifactsResults {
    SelectedArtifactResults select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector);
}
