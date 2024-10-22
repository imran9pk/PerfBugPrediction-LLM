package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DirectDependencyMetadataAdapter extends AbstractDependencyMetadataAdapter<DirectDependencyMetadata> implements DirectDependencyMetadata {

    public DirectDependencyMetadataAdapter(ImmutableAttributesFactory attributesFactory, List<ModuleDependencyMetadata> container, int originalIndex) {
        super(attributesFactory, container, originalIndex);
    }

    @Override
    public void endorseStrictVersions() {
        updateMetadata(getOriginalMetadata().withEndorseStrictVersions(true));
    }

    @Override
    public void doNotEndorseStrictVersions() {
        updateMetadata(getOriginalMetadata().withEndorseStrictVersions(false));
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return getOriginalMetadata().isEndorsingStrictVersions();
    }

    @Override
    public List<DependencyArtifact> getArtifactSelectors() {
        return getIvyArtifacts().stream().map(this::asDependencyArtifact).collect(Collectors.toList());
    }

    private DependencyArtifact asDependencyArtifact(IvyArtifactName ivyArtifactName) {
        return new DefaultDependencyArtifact(ivyArtifactName.getName(), ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getClassifier(), null);
    }

    private List<IvyArtifactName> getIvyArtifacts() {
        ModuleDependencyMetadata originalMetadata = getOriginalMetadata();
        if (originalMetadata instanceof ConfigurationBoundExternalDependencyMetadata) {
            ConfigurationBoundExternalDependencyMetadata externalMetadata = (ConfigurationBoundExternalDependencyMetadata) originalMetadata;
            ExternalDependencyDescriptor descriptor = externalMetadata.getDependencyDescriptor();
            if (descriptor instanceof MavenDependencyDescriptor) {
                return fromMavenDescriptor((MavenDependencyDescriptor) descriptor);
            }
            if (descriptor instanceof IvyDependencyDescriptor) {
                return fromIvyDescriptor((IvyDependencyDescriptor) descriptor);
            }
        } else if (originalMetadata instanceof GradleDependencyMetadata){
            return fromGradleMetadata((GradleDependencyMetadata) originalMetadata);
        }
        return Collections.emptyList();
    }

    private List<IvyArtifactName> fromGradleMetadata(GradleDependencyMetadata metadata) {
        IvyArtifactName artifact = metadata.getDependencyArtifact();
        if(artifact != null) {
            return Collections.singletonList(artifact);
        }
        return Collections.emptyList();
    }

    private List<IvyArtifactName> fromIvyDescriptor(IvyDependencyDescriptor descriptor) {
        List<Artifact> artifacts = descriptor.getDependencyArtifacts();
        return artifacts.stream().map(Artifact::getArtifactName).collect(Collectors.toList());
    }

    private List<IvyArtifactName> fromMavenDescriptor(MavenDependencyDescriptor descriptor) {
        IvyArtifactName artifact = descriptor.getDependencyArtifact();
        if(artifact != null) {
            return Collections.singletonList(artifact);
        }
        return Collections.emptyList();
    }

}
