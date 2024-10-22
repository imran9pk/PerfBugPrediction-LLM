package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.util.Set;

public class ReadOnlyModuleVersionsCache extends DefaultModuleVersionsCache {

    public ReadOnlyModuleVersionsCache(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        super(timeProvider, artifactCacheLockingManager, moduleIdentifierFactory);
    }

    @Override
    protected void store(ModuleAtRepositoryKey key, ModuleVersionsCacheEntry entry) {
        operationShouldNotHaveBeenCalled();
    }

    @Override
    public void cacheModuleVersionList(ModuleComponentRepository repository, ModuleIdentifier moduleId, Set<String> listedVersions) {
        throw operationShouldNotHaveBeenCalled();
    }

    private static UnsupportedOperationException operationShouldNotHaveBeenCalled() {
        throw new UnsupportedOperationException("A write operation shouldn't have been called in a read-only cache");
    }
}
