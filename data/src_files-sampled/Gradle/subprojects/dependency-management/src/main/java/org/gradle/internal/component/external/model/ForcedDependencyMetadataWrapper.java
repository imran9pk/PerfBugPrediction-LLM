package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.List;

public class ForcedDependencyMetadataWrapper implements ForcingDependencyMetadata, ModuleDependencyMetadata {
    private final ModuleDependencyMetadata delegate;

    public ForcedDependencyMetadataWrapper(ModuleDependencyMetadata delegate) {
        this.delegate = delegate;
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return delegate.getSelector();
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        return new ForcedDependencyMetadataWrapper(delegate.withRequestedVersion(requestedVersion));
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new ForcedDependencyMetadataWrapper(delegate.withReason(reason));
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        return new ForcedDependencyMetadataWrapper(delegate.withEndorseStrictVersions(endorse));
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        return delegate.selectConfigurations(consumerAttributes, targetComponent, consumerSchema, explicitRequestedCapabilities);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        DependencyMetadata dependencyMetadata = delegate.withTarget(target);
        if (dependencyMetadata instanceof DefaultProjectDependencyMetadata) {
            return ((DefaultProjectDependencyMetadata) dependencyMetadata).forced();
        }
        return new ForcedDependencyMetadataWrapper((ModuleDependencyMetadata) dependencyMetadata);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        DependencyMetadata dependencyMetadata = delegate.withTargetAndArtifacts(target, artifacts);
        if (dependencyMetadata instanceof DefaultProjectDependencyMetadata) {
            return ((DefaultProjectDependencyMetadata) dependencyMetadata).forced();
        }
        return new ForcedDependencyMetadataWrapper((ModuleDependencyMetadata) dependencyMetadata);
    }

    @Override
    public boolean isChanging() {
        return delegate.isChanging();
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public boolean isConstraint() {
        return delegate.isConstraint();
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return delegate.isEndorsingStrictVersions();
    }

    @Override
    public String getReason() {
        return delegate.getReason();
    }

    @Override
    public boolean isForce() {
        return true;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        return this;
    }

    public ModuleDependencyMetadata unwrap() {
        return delegate;
    }
}
