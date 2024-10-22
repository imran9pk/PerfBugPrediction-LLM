package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentArtifactsResolveResult;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet.NO_ARTIFACTS;

class RepositoryChainArtifactResolver implements ArtifactResolver, OriginArtifactSelector {
    private final Map<String, ModuleComponentRepository> repositories = new LinkedHashMap<>();
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public RepositoryChainArtifactResolver(CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    void add(ModuleComponentRepository repository) {
        repositories.put(repository.getId(), repository);
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSources());
        sourceRepository.getLocalAccess().resolveArtifactsWithType(component, artifactType, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result);
        }
    }

    @Nullable
    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ArtifactTypeRegistry artifactTypeRegistry, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        if (component.getSources() == null) {
            return NO_ARTIFACTS;
        }
        if (configuration.getArtifacts().isEmpty()) {
            AttributeValue<String> componentTypeEntry = configuration.getAttributes().findEntry(MavenImmutableAttributesFactory.CATEGORY_ATTRIBUTE);
            if (componentTypeEntry.isPresent()) {
                String value = componentTypeEntry.get();
                if (Category.REGULAR_PLATFORM.equals(value) || Category.ENFORCED_PLATFORM.equals(value)) {
                    return NO_ARTIFACTS;
                }
            }
        }
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSources());
        DefaultBuildableComponentArtifactsResolveResult result = new DefaultBuildableComponentArtifactsResolveResult();
        sourceRepository.getLocalAccess().resolveArtifacts(component, configuration, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifacts(component, configuration, result);
        }
        if (result.hasResult()) {
            return result.getResult().getArtifactsFor(component, configuration, this, sourceRepository.getArtifactCache(), artifactTypeRegistry, exclusions, overriddenAttributes, calculatedValueContainerFactory);
        }
        return null;
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources sources, BuildableArtifactResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(sources);

        sourceRepository.getLocalAccess().resolveArtifact(artifact, sources, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifact(artifact, sources, result);
        }
    }

    private ModuleComponentRepository findSourceRepository(ModuleSources sources) {
        RepositoryChainModuleSource repositoryChainModuleSource = sources.getSource(RepositoryChainModuleSource.class).get();
        ModuleComponentRepository moduleVersionRepository = repositories.get(repositoryChainModuleSource.getRepositoryId());
        if (moduleVersionRepository == null) {
            throw new IllegalStateException("Attempting to resolve artifacts from invalid repository");
        }
        return moduleVersionRepository;
    }

}
