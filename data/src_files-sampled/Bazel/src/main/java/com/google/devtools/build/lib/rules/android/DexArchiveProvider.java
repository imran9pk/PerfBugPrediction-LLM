package com.google.devtools.build.lib.rules.android;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Immutable
public class DexArchiveProvider implements TransitiveInfoProvider {

  public static final DexArchiveProvider NEVERLINK = new DexArchiveProvider.Builder().build();

  public static class Builder {

    private final Table<ImmutableSet<String>, Artifact, Artifact> dexArchives =
        HashBasedTable.create();
    private final NestedSetBuilder<ImmutableTable<ImmutableSet<String>, Artifact, Artifact>>
        transitiveDexArchives = NestedSetBuilder.stableOrder();

    public Builder() {}

    public Builder addTransitiveProviders(Iterable<DexArchiveProvider> providers) {
      for (DexArchiveProvider provider : providers) {
        transitiveDexArchives.addTransitive(provider.dexArchives);
      }
      return this;
    }

    public Builder addDexArchive(Set<String> dexopts, Artifact dexArchive, Artifact dexedJar) {
      checkArgument(
          dexArchive.getFilename().endsWith(".dex.zip"),
          "Doesn't look like a dex archive: %s",
          dexArchive);
      Artifact old =
          dexArchives.put(
              ImmutableSet.copyOf(dexopts), checkNotNull(dexedJar, "dexedJar"), dexArchive);
      checkArgument(
          old == null || old.equals(dexArchive),
          "We already had mapping %s-%s for dexopts %s, so we don't also need %s",
          dexedJar,
          old,
          dexopts,
          dexArchive);
      return this;
    }

    public DexArchiveProvider build() {
      return new DexArchiveProvider(
          transitiveDexArchives.add(ImmutableTable.copyOf(dexArchives)).build());
    }
  }

  private final NestedSet<ImmutableTable<ImmutableSet<String>, Artifact, Artifact>> dexArchives;

  private DexArchiveProvider(
      NestedSet<ImmutableTable<ImmutableSet<String>, Artifact, Artifact>> dexArchives) {
    this.dexArchives = dexArchives;
  }

  public Map<Artifact, Artifact> archivesForDexopts(ImmutableSet<String> dexopts) {
    LinkedHashMap<Artifact, Artifact> result = new LinkedHashMap<>();
    for (ImmutableTable<ImmutableSet<String>, Artifact, Artifact> partialMapping :
        dexArchives.toList()) {
      result.putAll(partialMapping.row(dexopts));
    }
    return result;
  }

  @Override
  public int hashCode() {
    return dexArchives.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DexArchiveProvider other = (DexArchiveProvider) obj;
    return dexArchives.equals(other.dexArchives);
  }
}
