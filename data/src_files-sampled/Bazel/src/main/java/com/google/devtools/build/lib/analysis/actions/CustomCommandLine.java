package com.google.devtools.build.lib.analysis.actions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Artifact.ArtifactExpander;
import com.google.devtools.build.lib.actions.Artifact.TreeFileArtifact;
import com.google.devtools.build.lib.actions.CommandLine;
import com.google.devtools.build.lib.actions.CommandLineExpansionException;
import com.google.devtools.build.lib.actions.CommandLineItem;
import com.google.devtools.build.lib.actions.SingleStringArgFormatter;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.concurrent.BlazeInterners;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.util.LazyString;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

@Immutable
@AutoCodec
public final class CustomCommandLine extends CommandLine {

  private interface ArgvFragment {
    int eval(List<Object> arguments, int argi, ImmutableList.Builder<String> builder)
        throws CommandLineExpansionException, InterruptedException;

    int addToFingerprint(
        List<Object> arguments,
        int argi,
        ActionKeyContext actionKeyContext,
        Fingerprint fingerprint)
        throws CommandLineExpansionException, InterruptedException;
  }

  private abstract static class StandardArgvFragment implements ArgvFragment {
    @Override
    public final int eval(List<Object> arguments, int argi, ImmutableList.Builder<String> builder) {
      eval(builder);
      return argi; }

    abstract void eval(ImmutableList.Builder<String> builder);

    @Override
    public int addToFingerprint(
        List<Object> arguments,
        int argi,
        ActionKeyContext actionKeyContext,
        Fingerprint fingerprint) {
      addToFingerprint(actionKeyContext, fingerprint);
      return argi; }

    abstract void addToFingerprint(ActionKeyContext actionKeyContext, Fingerprint fingerprint);
  }

  @AutoCodec
  public static class VectorArg<T> {
    final boolean isNestedSet;
    final boolean isEmpty;
    final int count;
    final String formatEach;
    final String beforeEach;
    final String joinWith;

    @AutoCodec.Instantiator
    @VisibleForSerialization
    VectorArg(
        boolean isNestedSet,
        boolean isEmpty,
        int count,
        String formatEach,
        String beforeEach,
        String joinWith) {
      this.isNestedSet = isNestedSet;
      this.isEmpty = isEmpty;
      this.count = count;
      this.formatEach = formatEach;
      this.beforeEach = beforeEach;
      this.joinWith = joinWith;
    }

    @AutoCodec
    public static class SimpleVectorArg<T> extends VectorArg<T> {
      private final Object values;

      private SimpleVectorArg(Builder builder, @Nullable Collection<T> values) {
        this(
            false,
            values == null || values.isEmpty(),
            values != null ? values.size() : 0,
            builder.formatEach,
            builder.beforeEach,
            builder.joinWith,
            values);
      }

      private SimpleVectorArg(Builder builder, @Nullable NestedSet<T> values) {
        this(
            true,
            values == null || values.isEmpty(),
            -1,
            builder.formatEach,
            builder.beforeEach,
            builder.joinWith,
            values);
      }

      @AutoCodec.Instantiator
      @VisibleForSerialization
      SimpleVectorArg(
          boolean isNestedSet,
          boolean isEmpty,
          int count,
          String formatEach,
          String beforeEach,
          String joinWith,
          @Nullable Object values) {
        super(isNestedSet, isEmpty, count, formatEach, beforeEach, joinWith);
        this.values = values;
      }

      public MappedVectorArg<T> mapped(CommandLineItem.MapFn<? super T> mapFn) {
        return new MappedVectorArg<>(this, mapFn);
      }
    }

    static class MappedVectorArg<T> extends VectorArg<String> {
      private final Object values;
      private final CommandLineItem.MapFn<? super T> mapFn;

      private MappedVectorArg(SimpleVectorArg<T> other, CommandLineItem.MapFn<? super T> mapFn) {
        super(
            other.isNestedSet,
            other.isEmpty,
            other.count,
            other.formatEach,
            other.beforeEach,
            other.joinWith);
        this.values = other.values;
        this.mapFn = mapFn;
      }
    }

    public static <T> SimpleVectorArg<T> of(Collection<T> values) {
      return new Builder().each(values);
    }

    public static <T> SimpleVectorArg<T> of(NestedSet<T> values) {
      return new Builder().each(values);
    }

    public static Builder format(@CompileTimeConstant String formatEach) {
      return new Builder().format(formatEach);
    }
    public static Builder addBefore(@CompileTimeConstant String beforeEach) {
      return new Builder().addBefore(beforeEach);
    }

    public static Builder join(String delimiter) {
      return new Builder().join(delimiter);
    }

    public static class Builder {
      private String formatEach;
      private String beforeEach;
      private String joinWith;

      public Builder format(@CompileTimeConstant String formatEach) {
        Preconditions.checkNotNull(formatEach);
        this.formatEach = formatEach;
        return this;
      }

      public Builder addBefore(@CompileTimeConstant String beforeEach) {
        Preconditions.checkNotNull(beforeEach);
        this.beforeEach = beforeEach;
        return this;
      }

      public Builder join(String delimiter) {
        Preconditions.checkNotNull(delimiter);
        this.joinWith = delimiter;
        return this;
      }

      public <T> SimpleVectorArg<T> each(Collection<T> values) {
        return new SimpleVectorArg<>(this, values);
      }

      public <T> SimpleVectorArg<T> each(NestedSet<T> values) {
        return new SimpleVectorArg<>(this, values);
      }
    }

    private static void push(List<Object> arguments, VectorArg<?> vectorArg) {
      final Object values;
      final CommandLineItem.MapFn<?> mapFn;
      if (vectorArg instanceof SimpleVectorArg) {
        values = ((SimpleVectorArg) vectorArg).values;
        mapFn = null;
      } else {
        values = ((MappedVectorArg) vectorArg).values;
        mapFn = ((MappedVectorArg) vectorArg).mapFn;
      }
      VectorArgFragment vectorArgFragment =
          new VectorArgFragment(
              vectorArg.isNestedSet,
              mapFn != null,
              vectorArg.formatEach != null,
              vectorArg.beforeEach != null,
              vectorArg.joinWith != null);
      if (vectorArgFragment.hasBeforeEach && vectorArgFragment.hasJoinWith) {
        throw new IllegalArgumentException("Cannot use both 'before' and 'join' in vector arg.");
      }
      vectorArgFragment = VectorArgFragment.interner.intern(vectorArgFragment);
      arguments.add(vectorArgFragment);
      if (vectorArgFragment.hasMapEach) {
        arguments.add(mapFn);
      }
      if (vectorArgFragment.isNestedSet) {
        arguments.add(values);
      } else {
        arguments.add(vectorArg.count);
        arguments.addAll((Collection<?>) values);
      }
      if (vectorArgFragment.hasFormatEach) {
        arguments.add(vectorArg.formatEach);
      }
      if (vectorArgFragment.hasBeforeEach) {
        arguments.add(vectorArg.beforeEach);
      }
      if (vectorArgFragment.hasJoinWith) {
        arguments.add(vectorArg.joinWith);
      }
    }

    @AutoCodec
    static final class VectorArgFragment implements ArgvFragment {
      private static Interner<VectorArgFragment> interner = BlazeInterners.newStrongInterner();
      private static final UUID FORMAT_EACH_UUID =
          UUID.fromString("f830781f-2e0d-4e3b-9b99-ece7f249e0f3");
      private static final UUID BEFORE_EACH_UUID =
          UUID.fromString("07d22a0d-2691-4f1c-9f47-5294de1f94e4");
      private static final UUID JOIN_WITH_UUID =
          UUID.fromString("c96ed6f0-9220-40f6-9e0c-1c0c5e0b47e4");

      private final boolean isNestedSet;
      private final boolean hasMapEach;
      private final boolean hasFormatEach;
      private final boolean hasBeforeEach;
      private final boolean hasJoinWith;

      @AutoCodec.Instantiator
      @VisibleForSerialization
      VectorArgFragment(
          boolean isNestedSet,
          boolean hasMapEach,
          boolean hasFormatEach,
          boolean hasBeforeEach,
          boolean hasJoinWith) {
        this.isNestedSet = isNestedSet;
        this.hasMapEach = hasMapEach;
        this.hasFormatEach = hasFormatEach;
        this.hasBeforeEach = hasBeforeEach;
        this.hasJoinWith = hasJoinWith;
      }

      @SuppressWarnings("unchecked")
      @Override
      public int eval(List<Object> arguments, int argi, ImmutableList.Builder<String> builder)
          throws CommandLineExpansionException, InterruptedException {
        final List<String> mutatedValues;
        CommandLineItem.MapFn<Object> mapFn =
            hasMapEach ? (CommandLineItem.MapFn<Object>) arguments.get(argi++) : null;
        if (isNestedSet) {
          NestedSet<Object> values = (NestedSet<Object>) arguments.get(argi++);
          ImmutableList<Object> list = values.toList();
          mutatedValues = new ArrayList<>(list.size());
          if (mapFn != null) {
            Consumer<String> args = mutatedValues::add; for (Object object : list) {
              mapFn.expandToCommandLine(object, args);
            }
          } else {
            for (Object object : list) {
              mutatedValues.add(CommandLineItem.expandToCommandLine(object));
            }
          }
        } else {
          int count = (Integer) arguments.get(argi++);
          mutatedValues = new ArrayList<>(count);
          if (mapFn != null) {
            Consumer<String> args = mutatedValues::add; for (int i = 0; i < count; ++i) {
              mapFn.expandToCommandLine(arguments.get(argi++), args);
            }
          } else {
            for (int i = 0; i < count; ++i) {
              mutatedValues.add(CommandLineItem.expandToCommandLine(arguments.get(argi++)));
            }
          }
        }
        final int count = mutatedValues.size();
        if (hasFormatEach) {
          String formatStr = (String) arguments.get(argi++);
          for (int i = 0; i < count; ++i) {
            mutatedValues.set(i, SingleStringArgFormatter.format(formatStr, mutatedValues.get(i)));
          }
        }
        if (hasBeforeEach) {
          String beforeEach = (String) arguments.get(argi++);
          for (int i = 0; i < count; ++i) {
            builder.add(beforeEach);
            builder.add(mutatedValues.get(i));
          }
        } else if (hasJoinWith) {
          String joinWith = (String) arguments.get(argi++);
          builder.add(Joiner.on(joinWith).join(mutatedValues));
        } else {
          for (int i = 0; i < count; ++i) {
            builder.add(mutatedValues.get(i));
          }
        }
        return argi;
      }

      @SuppressWarnings("unchecked")
      @Override
      public int addToFingerprint(
          List<Object> arguments,
          int argi,
          ActionKeyContext actionKeyContext,
          Fingerprint fingerprint)
          throws CommandLineExpansionException, InterruptedException {
        CommandLineItem.MapFn<Object> mapFn =
            hasMapEach ? (CommandLineItem.MapFn<Object>) arguments.get(argi++) : null;
        if (isNestedSet) {
          NestedSet<Object> values = (NestedSet<Object>) arguments.get(argi++);
          if (mapFn != null) {
            actionKeyContext.addNestedSetToFingerprint(mapFn, fingerprint, values);
          } else {
            actionKeyContext.addNestedSetToFingerprint(fingerprint, values);
          }
        } else {
          int count = (Integer) arguments.get(argi++);
          if (mapFn != null) {
            for (int i = 0; i < count; ++i) {
              mapFn.expandToCommandLine(arguments.get(argi++), fingerprint::addString);
            }
          } else {
            for (int i = 0; i < count; ++i) {
              fingerprint.addString(CommandLineItem.expandToCommandLine(arguments.get(argi++)));
            }
          }
        }
        if (hasFormatEach) {
          fingerprint.addUUID(FORMAT_EACH_UUID);
          fingerprint.addString((String) arguments.get(argi++));
        }
        if (hasBeforeEach) {
          fingerprint.addUUID(BEFORE_EACH_UUID);
          fingerprint.addString((String) arguments.get(argi++));
        } else if (hasJoinWith) {
          fingerprint.addUUID(JOIN_WITH_UUID);
          fingerprint.addString((String) arguments.get(argi++));
        }
        return argi;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        VectorArgFragment vectorArgFragment = (VectorArgFragment) o;
        return isNestedSet == vectorArgFragment.isNestedSet
            && hasMapEach == vectorArgFragment.hasMapEach
            && hasFormatEach == vectorArgFragment.hasFormatEach
            && hasBeforeEach == vectorArgFragment.hasBeforeEach
            && hasJoinWith == vectorArgFragment.hasJoinWith;
      }

      @Override
      public int hashCode() {
        return Objects.hashCode(isNestedSet, hasMapEach, hasFormatEach, hasBeforeEach, hasJoinWith);
      }
    }
  }

  @AutoCodec.VisibleForSerialization
  static class FormatArg implements ArgvFragment {
    @AutoCodec @AutoCodec.VisibleForSerialization static final FormatArg INSTANCE = new FormatArg();
    private static final UUID FORMAT_UUID = UUID.fromString("377cee34-e947-49e0-94a2-6ab95b396ec4");

    private static void push(List<Object> arguments, String formatStr, Object... args) {
      arguments.add(INSTANCE);
      arguments.add(args.length);
      arguments.add(formatStr);
      Collections.addAll(arguments, args);
    }

    @Override
    public int eval(List<Object> arguments, int argi, ImmutableList.Builder<String> builder) {
      int argCount = (Integer) arguments.get(argi++);
      String formatStr = (String) arguments.get(argi++);
      Object[] args = new Object[argCount];
      for (int i = 0; i < argCount; ++i) {
        args[i] = CommandLineItem.expandToCommandLine(arguments.get(argi++));
      }
      builder.add(String.format(formatStr, args));
      return argi;
    }

    @Override
    public int addToFingerprint(
        List<Object> arguments,
        int argi,
        ActionKeyContext actionKeyContext,
        Fingerprint fingerprint) {
      int argCount = (Integer) arguments.get(argi++);
      fingerprint.addUUID(FORMAT_UUID);
      fingerprint.addString((String) arguments.get(argi++));
      for (int i = 0; i < argCount; ++i) {
        fingerprint.addString(CommandLineItem.expandToCommandLine(arguments.get(argi++)));
      }
      return argi;
    }
  }

  @AutoCodec.VisibleForSerialization
  static class PrefixArg implements ArgvFragment {
    @AutoCodec @AutoCodec.VisibleForSerialization static final PrefixArg INSTANCE = new PrefixArg();
    private static final UUID PREFIX_UUID = UUID.fromString("a95eccdf-4f54-46fc-b925-c8c7e1f50c95");

    private static void push(List<Object> arguments, String before, Object arg) {
      arguments.add(INSTANCE);
      arguments.add(before);
      arguments.add(arg);
    }

    @Override
    public int eval(List<Object> arguments, int argi, ImmutableList.Builder<String> builder) {
      String before = (String) arguments.get(argi++);
      Object arg = arguments.get(argi++);
      builder.add(before + CommandLineItem.expandToCommandLine(arg));
      return argi;
    }

    @Override
    public int addToFingerprint(
        List<Object> arguments,
        int argi,
        ActionKeyContext actionKeyContext,
        Fingerprint fingerprint) {
      fingerprint.addUUID(PREFIX_UUID);
      fingerprint.addString((String) arguments.get(argi++));
      fingerprint.addString(CommandLineItem.expandToCommandLine(arguments.get(argi++)));
      return argi;
    }
  }

  private abstract static class TreeFileArtifactArgvFragment {
    abstract Object substituteTreeArtifact(Map<Artifact, TreeFileArtifact> substitutionMap);
  }

  private abstract static class TreeArtifactExpansionArgvFragment extends StandardArgvFragment {
    abstract void eval(ImmutableList.Builder<String> builder, ArtifactExpander artifactExpander);

    @Override
    void eval(ImmutableList.Builder<String> builder) {
      builder.add(describe());
    }

    abstract String describe();
  }

  @AutoCodec
  static final class ExpandedTreeArtifactArg extends TreeArtifactExpansionArgvFragment {
    private final Artifact treeArtifact;
    private static final UUID TREE_UUID = UUID.fromString("13b7626b-c77d-4a30-ad56-ff08c06b1cee");
    private final Function<Artifact, Iterable<String>> expandFunction;

    @AutoCodec.Instantiator
    @VisibleForSerialization
    ExpandedTreeArtifactArg(Artifact treeArtifact) {
      Preconditions.checkArgument(
          treeArtifact.isTreeArtifact(), "%s is not a TreeArtifact", treeArtifact);
      this.treeArtifact = treeArtifact;
      this.expandFunction = artifact -> ImmutableList.of(artifact.getExecPathString());
    }

    @VisibleForSerialization
    ExpandedTreeArtifactArg(
        Artifact treeArtifact, Function<Artifact, Iterable<String>> expandFunction) {
      Preconditions.checkArgument(
          treeArtifact.isTreeArtifact(), "%s is not a TreeArtifact", treeArtifact);
      this.treeArtifact = treeArtifact;
      this.expandFunction = expandFunction;
    }

    @Override
    void eval(ImmutableList.Builder<String> builder, ArtifactExpander artifactExpander) {
      Set<Artifact> expandedArtifacts = new TreeSet<>();
      artifactExpander.expand(treeArtifact, expandedArtifacts);

      for (Artifact expandedArtifact : expandedArtifacts) {
        for (String commandLine : expandFunction.apply(expandedArtifact)) {
          builder.add(commandLine);
        }
      }
    }

    @Override
    public String describe() {
      return String.format(
          "ExpandedTreeArtifactArg{ treeArtifact: %s}", treeArtifact.getExecPathString());
    }

    @Override
    void addToFingerprint(ActionKeyContext actionKeyContext, Fingerprint fingerprint) {
      fingerprint.addUUID(TREE_UUID);
      fingerprint.addPath(treeArtifact.getExecPath());
    }
  }

  private static final class TreeFileArtifactExecPathArg extends TreeFileArtifactArgvFragment {
    private final Artifact placeHolderTreeArtifact;

    private TreeFileArtifactExecPathArg(Artifact artifact) {
      Preconditions.checkArgument(artifact.isTreeArtifact(), "%s must be a TreeArtifact", artifact);
      placeHolderTreeArtifact = artifact;
    }

    @Override
    Object substituteTreeArtifact(Map<Artifact, TreeFileArtifact> substitutionMap) {
      Artifact artifact = substitutionMap.get(placeHolderTreeArtifact);
      Preconditions.checkNotNull(artifact, "Artifact to substitute: %s", placeHolderTreeArtifact);
      return artifact.getExecPath();
    }
  }

  public static final class Builder {
    private final List<Object> arguments = new ArrayList<>();

    public boolean isEmpty() {
      return arguments.isEmpty();
    }

    private final NestedSetBuilder<Artifact> treeArtifactInputs = NestedSetBuilder.stableOrder();

    private boolean treeArtifactsRequested = false;

    public Builder add(@CompileTimeConstant String value) {
      return addObjectInternal(value);
    }

    public Builder add(@CompileTimeConstant String arg, @Nullable String value) {
      return addObjectInternal(arg, value);
    }

    public Builder addDynamicString(@Nullable String value) {
      return addObjectInternal(value);
    }

    public Builder addLabel(@Nullable Label value) {
      return addObjectInternal(value);
    }

    public Builder addLabel(@CompileTimeConstant String arg, @Nullable Label value) {
      return addObjectInternal(arg, value);
    }

    public Builder addPath(@Nullable PathFragment value) {
      return addObjectInternal(value);
    }

    public Builder addPath(@CompileTimeConstant String arg, @Nullable PathFragment value) {
      return addObjectInternal(arg, value);
    }

    public Builder addExecPath(@Nullable Artifact value) {
      return addObjectInternal(value);
    }

    public Builder addExecPath(@CompileTimeConstant String arg, @Nullable Artifact value) {
      return addObjectInternal(arg, value);
    }

    public Builder addLazyString(@Nullable LazyString value) {
      return addObjectInternal(value);
    }

    public Builder addLazyString(@CompileTimeConstant String arg, @Nullable LazyString value) {
      return addObjectInternal(arg, value);
    }

    @FormatMethod
    public Builder addFormatted(@FormatString String formatStr, Object... args) {
      Preconditions.checkNotNull(formatStr);
      FormatArg.push(arguments, formatStr, args);
      return this;
    }

    public Builder addPrefixed(@CompileTimeConstant String prefix, @Nullable String arg) {
      return addPrefixedInternal(prefix, arg);
    }

    public Builder addPrefixedLabel(@CompileTimeConstant String prefix, @Nullable Label arg) {
      return addPrefixedInternal(prefix, arg);
    }

    public Builder addPrefixedPath(@CompileTimeConstant String prefix, @Nullable PathFragment arg) {
      return addPrefixedInternal(prefix, arg);
    }

    public Builder addPrefixedExecPath(@CompileTimeConstant String prefix, @Nullable Artifact arg) {
      return addPrefixedInternal(prefix, arg);
    }

    public Builder addAll(@Nullable Collection<String> values) {
      return addCollectionInternal(values);
    }

    public Builder addAll(@Nullable NestedSet<String> values) {
      return addNestedSetInternal(values);
    }

    public Builder addAll(@CompileTimeConstant String arg, @Nullable Collection<String> values) {
      return addCollectionInternal(arg, values);
    }

    public Builder addAll(@CompileTimeConstant String arg, @Nullable NestedSet<String> values) {
      return addNestedSetInternal(arg, values);
    }

    public Builder addAll(VectorArg<String> vectorArg) {
      return addVectorArgInternal(vectorArg);
    }

    public Builder addAll(@CompileTimeConstant String arg, VectorArg<String> vectorArg) {
      return addVectorArgInternal(arg, vectorArg);
    }

    public Builder addPaths(@Nullable Collection<PathFragment> values) {
      return addCollectionInternal(values);
    }

    public Builder addPaths(@Nullable NestedSet<PathFragment> values) {
      return addNestedSetInternal(values);
    }

    public Builder addPaths(
        @CompileTimeConstant String arg, @Nullable Collection<PathFragment> values) {
      return addCollectionInternal(arg, values);
    }

    public Builder addPaths(
        @CompileTimeConstant String arg, @Nullable NestedSet<PathFragment> values) {
      return addNestedSetInternal(arg, values);
    }

    public Builder addPaths(VectorArg<PathFragment> vectorArg) {
      return addVectorArgInternal(vectorArg);
    }

    public Builder addPaths(@CompileTimeConstant String arg, VectorArg<PathFragment> vectorArg) {
      return addVectorArgInternal(arg, vectorArg);
    }

    public Builder addExecPaths(@Nullable Collection<Artifact> values) {
      return addCollectionInternal(values);
    }

    public Builder addExecPaths(@Nullable NestedSet<Artifact> values) {
      return addNestedSetInternal(values);
    }

    public Builder addExecPaths(
        @CompileTimeConstant String arg, @Nullable Collection<Artifact> values) {
      return addCollectionInternal(arg, values);
    }

    public Builder addExecPaths(
        @CompileTimeConstant String arg, @Nullable NestedSet<Artifact> values) {
      return addNestedSetInternal(arg, values);
    }

    public Builder addExecPaths(VectorArg<Artifact> vectorArg) {
      return addVectorArgInternal(vectorArg);
    }

    public Builder addExecPaths(@CompileTimeConstant String arg, VectorArg<Artifact> vectorArg) {
      return addVectorArgInternal(arg, vectorArg);
    }

    public Builder addPlaceholderTreeArtifactExecPath(@Nullable Artifact treeArtifact) {
      if (treeArtifact != null) {
        Preconditions.checkState(!treeArtifactsRequested);
        treeArtifactInputs.add(treeArtifact);
        arguments.add(new TreeFileArtifactExecPathArg(treeArtifact));
      }
      return this;
    }

    public Builder addPlaceholderTreeArtifactExecPath(String arg, @Nullable Artifact treeArtifact) {
      Preconditions.checkNotNull(arg);
      if (treeArtifact != null) {
        Preconditions.checkState(!treeArtifactsRequested);
        treeArtifactInputs.add(treeArtifact);
        arguments.add(arg);
        arguments.add(new TreeFileArtifactExecPathArg(treeArtifact));
      }
      return this;
    }

    public Builder addExpandedTreeArtifactExecPaths(Artifact treeArtifact) {
      Preconditions.checkState(!treeArtifactsRequested);
      treeArtifactInputs.add(treeArtifact);
      Preconditions.checkNotNull(treeArtifact);
      arguments.add(new ExpandedTreeArtifactArg(treeArtifact));
      return this;
    }

    public Builder addExpandedTreeArtifactExecPaths(String arg, Artifact treeArtifact) {
      Preconditions.checkNotNull(arg);
      Preconditions.checkNotNull(treeArtifact);
      Preconditions.checkState(!treeArtifactsRequested);
      treeArtifactInputs.add(treeArtifact);
      arguments.add(
          new ExpandedTreeArtifactArg(
              treeArtifact, artifact -> ImmutableList.of(arg, artifact.getExecPathString())));
      return this;
    }

    public Builder addExpandedTreeArtifact(
        Artifact treeArtifact, Function<Artifact, Iterable<String>> expandFunction) {
      Preconditions.checkNotNull(treeArtifact);
      Preconditions.checkState(!treeArtifactsRequested);
      treeArtifactInputs.add(treeArtifact);
      arguments.add(new ExpandedTreeArtifactArg(treeArtifact, expandFunction));
      return this;
    }

    public NestedSet<Artifact> getTreeArtifactInputs() {
      treeArtifactsRequested = true;
      return treeArtifactInputs.build();
    }

    public CustomCommandLine build() {
      return new CustomCommandLine(arguments);
    }

    private Builder addObjectInternal(@Nullable Object value) {
      if (value != null) {
        arguments.add(value);
      }
      return this;
    }

    private Builder addObjectInternal(@CompileTimeConstant String arg, @Nullable Object value) {
      Preconditions.checkNotNull(arg);
      if (value != null) {
        arguments.add(arg);
        addObjectInternal(value);
      }
      return this;
    }

    private Builder addPrefixedInternal(String prefix, @Nullable Object arg) {
      Preconditions.checkNotNull(prefix);
      if (arg != null) {
        PrefixArg.push(arguments, prefix, arg);
      }
      return this;
    }

    private Builder addCollectionInternal(@Nullable Collection<?> values) {
      if (values != null) {
        addVectorArgInternal(VectorArg.of(values));
      }
      return this;
    }

    private Builder addCollectionInternal(
        @CompileTimeConstant String arg, @Nullable Collection<?> values) {
      Preconditions.checkNotNull(arg);
      if (values != null && !values.isEmpty()) {
        arguments.add(arg);
        addCollectionInternal(values);
      }
      return this;
    }

    private Builder addNestedSetInternal(@Nullable NestedSet<?> values) {
      if (values != null) {
        arguments.add(values);
      }
      return this;
    }

    private Builder addNestedSetInternal(
        @CompileTimeConstant String arg, @Nullable NestedSet<?> values) {
      Preconditions.checkNotNull(arg);
      if (values != null && !values.isEmpty()) {
        arguments.add(arg);
        addNestedSetInternal(values);
      }
      return this;
    }

    private Builder addVectorArgInternal(VectorArg<?> vectorArg) {
      if (!vectorArg.isEmpty) {
        VectorArg.push(arguments, vectorArg);
      }
      return this;
    }

    private Builder addVectorArgInternal(@CompileTimeConstant String arg, VectorArg<?> vectorArg) {
      Preconditions.checkNotNull(arg);
      if (!vectorArg.isEmpty) {
        arguments.add(arg);
        addVectorArgInternal(vectorArg);
      }
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(Builder other) {
    Builder builder = new Builder();
    builder.arguments.addAll(other.arguments);
    return builder;
  }
  
  private final ImmutableList<Object> arguments;

  private final Map<Artifact, TreeFileArtifact> substitutionMap;

  private CustomCommandLine(List<Object> arguments) {
    this(arguments, null);
  }

  @AutoCodec.Instantiator
  @VisibleForSerialization
  CustomCommandLine(List<Object> arguments, Map<Artifact, TreeFileArtifact> substitutionMap) {
    this.arguments = ImmutableList.copyOf(arguments);
    this.substitutionMap = substitutionMap == null ? null : ImmutableMap.copyOf(substitutionMap);
  }

  @VisibleForTesting
  public CustomCommandLine evaluateTreeFileArtifacts(Iterable<TreeFileArtifact> treeFileArtifacts) {
    ImmutableMap.Builder<Artifact, TreeFileArtifact> substitutionMap = ImmutableMap.builder();
    for (TreeFileArtifact treeFileArtifact : treeFileArtifacts) {
      substitutionMap.put(treeFileArtifact.getParent(), treeFileArtifact);
    }

    return new CustomCommandLine(arguments, substitutionMap.build());
  }

  @Override
  public ImmutableList<String> arguments()
      throws CommandLineExpansionException, InterruptedException {
    return argumentsInternal(null);
  }

  @Override
  public ImmutableList<String> arguments(ArtifactExpander artifactExpander)
      throws CommandLineExpansionException, InterruptedException {
    return argumentsInternal(Preconditions.checkNotNull(artifactExpander));
  }

  private ImmutableList<String> argumentsInternal(@Nullable ArtifactExpander artifactExpander)
      throws CommandLineExpansionException, InterruptedException {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    int count = arguments.size();
    for (int i = 0; i < count; ) {
      Object arg = arguments.get(i++);
      Object substitutedArg = substituteTreeFileArtifactArgvFragment(arg);
      if (substitutedArg instanceof NestedSet) {
        evalSimpleVectorArg(((NestedSet<?>) substitutedArg).toList(), builder);
      } else if (substitutedArg instanceof Iterable) {
        evalSimpleVectorArg((Iterable<?>) substitutedArg, builder);
      } else if (substitutedArg instanceof ArgvFragment) {
        if (artifactExpander != null
            && substitutedArg instanceof TreeArtifactExpansionArgvFragment) {
          TreeArtifactExpansionArgvFragment expansionArg =
              (TreeArtifactExpansionArgvFragment) substitutedArg;
          expansionArg.eval(builder, artifactExpander);
        } else {
          i = ((ArgvFragment) substitutedArg).eval(arguments, i, builder);
        }
      } else {
        builder.add(CommandLineItem.expandToCommandLine(substitutedArg));
      }
    }
    return builder.build();
  }

  private void evalSimpleVectorArg(Iterable<?> arg, ImmutableList.Builder<String> builder) {
    for (Object value : arg) {
      builder.add(CommandLineItem.expandToCommandLine(value));
    }
  }

  private Object substituteTreeFileArtifactArgvFragment(Object arg) {
    if (arg instanceof TreeFileArtifactArgvFragment) {
      TreeFileArtifactArgvFragment argvFragment = (TreeFileArtifactArgvFragment) arg;
      return argvFragment.substituteTreeArtifact(
          Preconditions.checkNotNull(substitutionMap, argvFragment));
    } else {
      return arg;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void addToFingerprint(
      ActionKeyContext actionKeyContext,
      @Nullable ArtifactExpander artifactExpander,
      Fingerprint fingerprint)
      throws CommandLineExpansionException, InterruptedException {
    int count = arguments.size();
    for (int i = 0; i < count; ) {
      Object arg = arguments.get(i++);
      Object substitutedArg = substituteTreeFileArtifactArgvFragment(arg);
      if (substitutedArg instanceof NestedSet) {
        actionKeyContext.addNestedSetToFingerprint(fingerprint, (NestedSet<Object>) substitutedArg);
      } else if (substitutedArg instanceof Iterable) {
        for (Object value : (Iterable<Object>) substitutedArg) {
          fingerprint.addString(CommandLineItem.expandToCommandLine(value));
        }
      } else if (substitutedArg instanceof ArgvFragment) {
        i =
            ((ArgvFragment) substitutedArg)
                .addToFingerprint(arguments, i, actionKeyContext, fingerprint);
      } else {
        fingerprint.addString(CommandLineItem.expandToCommandLine(substitutedArg));
      }
    }
  }
}
