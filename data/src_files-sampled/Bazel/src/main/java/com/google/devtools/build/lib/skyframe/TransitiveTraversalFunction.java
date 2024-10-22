package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.AdvertisedProviderSet;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.NoSuchThingException;
import com.google.devtools.build.lib.util.GroupedList;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import com.google.devtools.build.skyframe.ValueOrException2;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class TransitiveTraversalFunction
    extends TransitiveBaseTraversalFunction<
        TransitiveTraversalFunction.DeterministicErrorMessageAccumulator> {

  @Override
  Label argumentFromKey(SkyKey key) {
    return (Label) key.argument();
  }

  @Override
  SkyKey getKey(Label label) {
    return TransitiveTraversalValue.key(label);
  }

  @Override
  DeterministicErrorMessageAccumulator processTarget(TargetAndErrorIfAny targetAndErrorIfAny) {
    NoSuchTargetException errorIfAny = targetAndErrorIfAny.getErrorLoadingTarget();
    String errorMessageIfAny = errorIfAny == null ? null : errorIfAny.getMessage();
    return DeterministicErrorMessageAccumulator.create(errorMessageIfAny);
  }

  @Override
  void processDeps(
      DeterministicErrorMessageAccumulator accumulator,
      EventHandler eventHandler,
      TargetAndErrorIfAny targetAndErrorIfAny,
      Iterable<Map.Entry<SkyKey, ValueOrException2<NoSuchPackageException, NoSuchTargetException>>>
          depEntries) {
    for (Map.Entry<SkyKey, ValueOrException2<NoSuchPackageException, NoSuchTargetException>> entry :
        depEntries) {
      TransitiveTraversalValue transitiveTraversalValue;
      try {
        transitiveTraversalValue = (TransitiveTraversalValue) entry.getValue().get();
        if (transitiveTraversalValue == null) {
          continue;
        }
      } catch (NoSuchPackageException | NoSuchTargetException e) {
        accumulator.maybeSet(e.getMessage());
        continue;
      }
      String errorMessage = transitiveTraversalValue.getErrorMessage();
      if (errorMessage != null) {
        accumulator.maybeSet(errorMessage);
      }
    }
  }

  @Nullable
  @Override
  protected AdvertisedProviderSet getAdvertisedProviderSet(
      Label toLabel,
      @Nullable ValueOrException2<NoSuchPackageException, NoSuchTargetException> toVal,
      Environment env) {
    if (toVal == null) {
      return null;
    }
    try {
      return ((TransitiveTraversalValue) toVal.get()).getProviders();
    } catch (NoSuchThingException e) {
      return null;
    }
  }

  @Override
  SkyValue computeSkyValue(
      TargetAndErrorIfAny targetAndErrorIfAny, DeterministicErrorMessageAccumulator accumulator) {
    boolean targetLoadedSuccessfully = targetAndErrorIfAny.getErrorLoadingTarget() == null;
    String errorMessage = accumulator.getErrorMessage();
    return targetLoadedSuccessfully
        ? TransitiveTraversalValue.forTarget(targetAndErrorIfAny.getTarget(), errorMessage)
        : TransitiveTraversalValue.unsuccessfulTransitiveTraversal(
            errorMessage, targetAndErrorIfAny.getTarget());
  }

  @Override
  Collection<SkyKey> getLabelDepKeys(
      SkyFunction.Environment env, TargetAndErrorIfAny targetAndErrorIfAny)
      throws InterruptedException {
    Collection<SkyKey> oldDepKeys = getDepsAfterLastPackageDep(env, 1);
    return oldDepKeys == null ? super.getLabelDepKeys(env, targetAndErrorIfAny) : oldDepKeys;
  }

  @Override
  Iterable<SkyKey> getStrictLabelAspectDepKeys(
      SkyFunction.Environment env,
      Map<SkyKey, ValueOrException2<NoSuchPackageException, NoSuchTargetException>> depMap,
      TargetAndErrorIfAny targetAndErrorIfAny)
      throws InterruptedException {
    Collection<SkyKey> oldAspectDepKeys = getDepsAfterLastPackageDep(env, 2);
    return oldAspectDepKeys == null
        ? super.getStrictLabelAspectDepKeys(env, depMap, targetAndErrorIfAny)
        : oldAspectDepKeys;
  }

  @Nullable
  private static Collection<SkyKey> getDepsAfterLastPackageDep(
      SkyFunction.Environment env, int offset) {
    GroupedList<SkyKey> temporaryDirectDeps = env.getTemporaryDirectDeps();
    if (temporaryDirectDeps == null) {
      return null;
    }
    int lastPackageDepIndex = getLastPackageValueIndex(temporaryDirectDeps);
    if (lastPackageDepIndex == -1
        || temporaryDirectDeps.listSize() <= lastPackageDepIndex + offset) {
      return null;
    }
    return temporaryDirectDeps.get(lastPackageDepIndex + offset);
  }

  private static int getLastPackageValueIndex(GroupedList<SkyKey> directDeps) {
    int directDepsNumGroups = directDeps.listSize();
    for (int i = directDepsNumGroups - 1; i >= 0; i--) {
      List<SkyKey> depGroup = directDeps.get(i);
      if (depGroup.size() == 1 && depGroup.get(0).functionName().equals(SkyFunctions.PACKAGE)) {
        return i;
      }
    }
    return -1;
  }

  interface DeterministicErrorMessageAccumulator {
    @Nullable
    String getErrorMessage();

    default void maybeSet(String errorMessage) {}

    static DeterministicErrorMessageAccumulator create(@Nullable String errorMessage) {
      if (errorMessage != null) {
        return () -> errorMessage;
      }
      return new UpdateableErrorMessageAccumulator();
    }

    class UpdateableErrorMessageAccumulator implements DeterministicErrorMessageAccumulator {
      private static final Comparator<String> LENGTH_THEN_ALPHABETICAL =
          Comparator.nullsLast(
              Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

      @Nullable private String errorMessage;

      @Override
      public void maybeSet(String errorMessage) {
        Preconditions.checkNotNull(errorMessage);
        if (LENGTH_THEN_ALPHABETICAL.compare(this.errorMessage, errorMessage) > 0) {
          this.errorMessage = errorMessage;
        }
      }

      @Nullable
      @Override
      public String getErrorMessage() {
        return errorMessage;
      }
    }
  }
}
