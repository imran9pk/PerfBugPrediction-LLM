package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import javax.annotation.Nullable;

public interface DependencyDescriptorFactory {
    LocalOriginDependencyMetadata createDependencyDescriptor(ComponentIdentifier componentId, @Nullable String clientConfiguration, @Nullable AttributeContainer attributes, ModuleDependency dependency);
    LocalOriginDependencyMetadata createDependencyConstraintDescriptor(ComponentIdentifier componentId, String clientConfiguration, AttributeContainer attributes, DependencyConstraint dependencyConstraint);
}
