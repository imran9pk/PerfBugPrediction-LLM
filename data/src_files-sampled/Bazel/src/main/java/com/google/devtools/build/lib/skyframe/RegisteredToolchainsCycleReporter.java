package com.google.devtools.build.lib.skyframe;

import static java.util.stream.Collectors.joining;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.ExtendedEventHandler;
import com.google.devtools.build.lib.skyframe.SingleToolchainResolutionValue.SingleToolchainResolutionKey;
import com.google.devtools.build.skyframe.CycleInfo;
import com.google.devtools.build.skyframe.CyclesReporter;
import com.google.devtools.build.skyframe.SkyKey;
import java.util.Optional;

public class RegisteredToolchainsCycleReporter implements CyclesReporter.SingleCycleReporter {

  private static final Predicate<SkyKey> IS_REGISTERED_TOOLCHAINS_SKY_KEY =
      SkyFunctions.isSkyFunction(SkyFunctions.REGISTERED_TOOLCHAINS);

  private static final Predicate<SkyKey> IS_CONFIGURED_TARGET_SKY_KEY =
      SkyFunctions.isSkyFunction(SkyFunctions.CONFIGURED_TARGET);

  private static final Predicate<SkyKey> IS_SINGLE_TOOLCHAIN_RESOLUTION_SKY_KEY =
      SkyFunctions.isSkyFunction(SkyFunctions.SINGLE_TOOLCHAIN_RESOLUTION);

  private static final Predicate<SkyKey> IS_TOOLCHAIN_RESOLUTION_SKY_KEY =
      SkyFunctions.isSkyFunction(SkyFunctions.TOOLCHAIN_RESOLUTION);

  private static final Predicate<SkyKey> IS_TOOLCHAIN_RELATED =
      Predicates.or(
          IS_REGISTERED_TOOLCHAINS_SKY_KEY,
          IS_SINGLE_TOOLCHAIN_RESOLUTION_SKY_KEY,
          IS_TOOLCHAIN_RESOLUTION_SKY_KEY);

  @Override
  public boolean maybeReportCycle(
      SkyKey topLevelKey,
      CycleInfo cycleInfo,
      boolean alreadyReported,
      ExtendedEventHandler eventHandler) {
    ImmutableList<SkyKey> cycle = cycleInfo.getCycle();
    if (alreadyReported) {
      return true;
    } else if (!Iterables.any(cycle, IS_TOOLCHAIN_RELATED)) {
      return false;
    }

    Optional<ConfiguredTargetKey> configuredTargetKey = findRootConfiguredTarget(cycle);
    if (!configuredTargetKey.isPresent()) {
      return false;
    }

    Function<SkyKey, String> printer =
        input -> {
          if (input.argument() instanceof ConfiguredTargetKey) {
            Label label = ((ConfiguredTargetKey) input.argument()).getLabel();
            return label.toString();
          }
          if (input.argument() instanceof RegisteredToolchainsValue.Key) {
            return "RegisteredToolchains";
          }
          if (input.argument() instanceof SingleToolchainResolutionKey) {
            Label toolchainType =
                ((SingleToolchainResolutionKey) input.argument()).toolchainTypeLabel();
            return String.format("toolchain type %s", toolchainType);
          }
          if (input.argument() instanceof ToolchainContextKey) {
            ImmutableSet<Label> toolchainTypes =
                ((ToolchainContextKey) input.argument()).requiredToolchainTypeLabels();
            return String.format(
                "toolchain types %s",
                toolchainTypes.stream().map(Label::toString).collect(joining(", ")));
          }

          throw new UnsupportedOperationException();
        };

    StringBuilder cycleMessage =
        new StringBuilder()
            .append("Misconfigured toolchains: ")
            .append(printer.apply(configuredTargetKey.get()))
            .append(" is declared as a toolchain but has inappropriate dependencies.")
            .append(" Declared toolchains should be created with the 'toolchain' rule")
            .append(" and should not have dependencies that themselves require toolchains.");

    AbstractLabelCycleReporter.printCycle(cycleInfo.getCycle(), cycleMessage, printer);
    eventHandler.handle(Event.error(null, cycleMessage.toString()));
    return true;
  }

  private Optional<ConfiguredTargetKey> findRootConfiguredTarget(ImmutableList<SkyKey> cycle) {
    boolean rtvFound = false;
    for (int i = 0; i < cycle.size() * 2; i++) {
      SkyKey skyKey = cycle.get(i % cycle.size());
      if (!rtvFound && IS_REGISTERED_TOOLCHAINS_SKY_KEY.apply(skyKey)) {
        rtvFound = true;
      }
      if (rtvFound && IS_CONFIGURED_TARGET_SKY_KEY.apply(skyKey)) {
        return Optional.of((ConfiguredTargetKey) skyKey);
      }
    }

    return Optional.empty();
  }
}
