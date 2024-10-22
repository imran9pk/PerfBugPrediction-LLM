package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.ArrayList;
import java.util.List;

public class DefaultResolvedArtifactsBuilder implements DependencyArtifactsVisitor {
    private final boolean buildProjectDependencies;
    private final ResolutionStrategy.SortOrder sortOrder;
    private final List<ArtifactSet> artifactSetsById = new ArrayList<>();

    public DefaultResolvedArtifactsBuilder(boolean buildProjectDependencies, ResolutionStrategy.SortOrder sortOrder) {
        this.buildProjectDependencies = buildProjectDependencies;
        this.sortOrder = sortOrder;
    }

    @Override
    public void startArtifacts(RootGraphNode root) {
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifacts) {
        collectArtifacts(artifactSetId, artifacts);
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {
        if (!buildProjectDependencies) {
            artifacts = new NoBuildDependenciesArtifactSet(artifacts);
        }
        collectArtifacts(artifactSetId, artifacts);
    }

    private void collectArtifacts(int artifactSetId, ArtifactSet artifacts) {
        assert artifactSetsById.size() >= artifactSetId;
        if (artifactSetsById.size() == artifactSetId) {
            artifactSetsById.add(artifacts);
        }
    }

    @Override
    public void finishArtifacts() {
    }

    public VisitedArtifactsResults complete() {
        return new DefaultVisitedArtifactResults(sortOrder, artifactSetsById);
    }
}
