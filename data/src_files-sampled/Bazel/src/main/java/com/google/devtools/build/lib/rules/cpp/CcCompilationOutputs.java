package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.starlarkbuildapi.cpp.CcCompilationOutputsApi;
import java.util.LinkedHashSet;
import java.util.Set;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;

public class CcCompilationOutputs implements CcCompilationOutputsApi<Artifact> {
  public static final CcCompilationOutputs EMPTY = builder().build();

  private final ImmutableList<Artifact> objectFiles;

  private final ImmutableList<Artifact> picObjectFiles;

  private final LtoCompilationContext ltoCompilationContext;

  private final ImmutableList<Artifact> dwoFiles;

  private final ImmutableList<Artifact> picDwoFiles;

  private final NestedSet<Artifact> temps;

  private final ImmutableList<Artifact> headerTokenFiles;

  private CcCompilationOutputs(
      ImmutableList<Artifact> objectFiles,
      ImmutableList<Artifact> picObjectFiles,
      LtoCompilationContext ltoCompilationContext,
      ImmutableList<Artifact> dwoFiles,
      ImmutableList<Artifact> picDwoFiles,
      NestedSet<Artifact> temps,
      ImmutableList<Artifact> headerTokenFiles) {
    this.objectFiles = objectFiles;
    this.picObjectFiles = picObjectFiles;
    this.ltoCompilationContext = ltoCompilationContext;
    this.dwoFiles = dwoFiles;
    this.picDwoFiles = picDwoFiles;
    this.temps = temps;
    this.headerTokenFiles = headerTokenFiles;
  }

  public boolean isEmpty() {
    return picObjectFiles.isEmpty() && objectFiles.isEmpty();
  }

  public ImmutableList<Artifact> getObjectFiles(boolean usePic) {
    return usePic ? picObjectFiles : objectFiles;
  }

  @Override
  public Sequence<Artifact> getStarlarkObjects() throws EvalException {
    return StarlarkList.immutableCopyOf(getObjectFiles(false));
  }

  @Override
  public Sequence<Artifact> getStarlarkPicObjects() throws EvalException {
    return StarlarkList.immutableCopyOf(getObjectFiles(true));
  }

  public LtoCompilationContext getLtoCompilationContext() {
    return ltoCompilationContext;
  }

  public ImmutableList<Artifact> getDwoFiles() {
    return dwoFiles;
  }

  public ImmutableList<Artifact> getPicDwoFiles() {
    return picDwoFiles;
  }

  public NestedSet<Artifact> getTemps() {
    return temps;
  }

  public Iterable<Artifact> getHeaderTokenFiles() {
    return headerTokenFiles;
  }

  NestedSet<Artifact> getFilesToCompile(boolean parseHeaders, boolean usePic) {
    NestedSetBuilder<Artifact> files = NestedSetBuilder.stableOrder();
    files.addAll(getObjectFiles(usePic));
    if (parseHeaders) {
      files.addAll(getHeaderTokenFiles());
    }
    return files.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Set<Artifact> objectFiles = new LinkedHashSet<>();
    private final Set<Artifact> picObjectFiles = new LinkedHashSet<>();
    private final LtoCompilationContext.Builder ltoCompilationContext =
        new LtoCompilationContext.Builder();
    private final Set<Artifact> dwoFiles = new LinkedHashSet<>();
    private final Set<Artifact> picDwoFiles = new LinkedHashSet<>();
    private final NestedSetBuilder<Artifact> temps = NestedSetBuilder.stableOrder();
    private final Set<Artifact> headerTokenFiles = new LinkedHashSet<>();

    private Builder() {
      }

    public CcCompilationOutputs build() {
      return new CcCompilationOutputs(
          ImmutableList.copyOf(objectFiles),
          ImmutableList.copyOf(picObjectFiles),
          ltoCompilationContext.build(),
          ImmutableList.copyOf(dwoFiles),
          ImmutableList.copyOf(picDwoFiles),
          temps.build(),
          ImmutableList.copyOf(headerTokenFiles));
    }

    public Builder merge(CcCompilationOutputs outputs) {
      this.objectFiles.addAll(outputs.objectFiles);
      this.picObjectFiles.addAll(outputs.picObjectFiles);
      this.dwoFiles.addAll(outputs.dwoFiles);
      this.picDwoFiles.addAll(outputs.picDwoFiles);
      this.temps.addTransitive(outputs.temps);
      this.headerTokenFiles.addAll(outputs.headerTokenFiles);
      this.ltoCompilationContext.addAll(outputs.ltoCompilationContext);
      return this;
    }

    public Builder addObjectFile(Artifact artifact) {
      Preconditions.checkArgument(
          artifact.isTreeArtifact() || Link.OBJECT_FILETYPES.matches(artifact.getFilename()));
      objectFiles.add(artifact);
      return this;
    }

    public Builder addObjectFiles(Iterable<Artifact> artifacts) {
      for (Artifact artifact : artifacts) {
        Preconditions.checkArgument(
            artifact.isTreeArtifact() || Link.OBJECT_FILETYPES.matches(artifact.getFilename()));
      }
      Iterables.addAll(objectFiles, artifacts);
      return this;
    }

    public Builder addPicObjectFile(Artifact artifact) {
      picObjectFiles.add(artifact);
      return this;
    }

    public Builder addLtoBitcodeFile(
        Artifact fullBitcode, Artifact ltoIndexingBitcode, ImmutableList<String> copts) {
      ltoCompilationContext.addBitcodeFile(fullBitcode, ltoIndexingBitcode, copts);
      return this;
    }

    public Builder addLtoCompilationContext(LtoCompilationContext ltoCompilationContext) {
      this.ltoCompilationContext.addAll(ltoCompilationContext);
      return this;
    }

    public Builder addPicObjectFiles(Iterable<Artifact> artifacts) {
      for (Artifact artifact : artifacts) {
        Preconditions.checkArgument(
            artifact.isTreeArtifact() || Link.OBJECT_FILETYPES.matches(artifact.getFilename()));
      }

      Iterables.addAll(picObjectFiles, artifacts);
      return this;
    }

    public Builder addDwoFile(Artifact artifact) {
      dwoFiles.add(artifact);
      return this;
    }

    public Builder addPicDwoFile(Artifact artifact) {
      picDwoFiles.add(artifact);
      return this;
    }

    public Builder addTemps(Iterable<Artifact> artifacts) {
      temps.addAll(artifacts);
      return this;
    }

    public Builder addHeaderTokenFile(Artifact artifact) {
      headerTokenFiles.add(artifact);
      return this;
    }
  }
}
