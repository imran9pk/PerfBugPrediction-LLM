package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class DefaultCachedArtifact implements CachedArtifact, Serializable {
    private final File cachedFile;
    private final long cachedAt;
    private final HashCode descriptorHash;
    private final List<String> attemptedLocations;

    public DefaultCachedArtifact(File cachedFile, long cachedAt, HashCode descriptorHash) {
        this.cachedFile = cachedFile;
        this.cachedAt = cachedAt;
        this.descriptorHash = descriptorHash;
        this.attemptedLocations = Collections.emptyList();
    }

    public DefaultCachedArtifact(List<String> attemptedLocations, long cachedAt, HashCode descriptorHash) {
        this.attemptedLocations = attemptedLocations;
        this.cachedAt = cachedAt;
        this.cachedFile = null;
        this.descriptorHash = descriptorHash;
    }

    @Override
    public boolean isMissing() {
        return cachedFile == null;
    }

    @Override
    public File getCachedFile() {
        return cachedFile;
    }

    @Override
    public long getCachedAt() {
        return cachedAt;
    }

    @Override
    public HashCode getDescriptorHash() {
        return descriptorHash;
    }

    @Override
    public List<String> attemptedLocations() {
        return attemptedLocations;
    }
}
