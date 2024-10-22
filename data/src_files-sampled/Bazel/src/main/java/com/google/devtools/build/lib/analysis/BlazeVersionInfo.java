package com.google.devtools.build.lib.analysis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.flogger.GoogleLogger;
import com.google.devtools.build.lib.util.StringUtilities;
import java.util.Date;
import java.util.Map;

public class BlazeVersionInfo {
  public static final String BUILD_LABEL = "Build label";
  public static final String BUILD_TIMESTAMP = "Build timestamp as int";

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static BlazeVersionInfo instance = null;

  private final Map<String, String> buildData = Maps.newTreeMap();

  public BlazeVersionInfo(Map<String, String> info) {
    buildData.putAll(info);
  }

  public static synchronized BlazeVersionInfo instance() {
    if (instance == null) {
      return new BlazeVersionInfo(ImmutableMap.<String, String>of());
    }
    return instance;
  }

  private static void logVersionInfo(BlazeVersionInfo info) {
    if (info.getSummary() == null) {
      logger.atWarning().log("Bazel release version information not available");
    } else {
      logger.atInfo().log("Bazel version info: %s", info.getSummary());
    }
  }

  public static synchronized void setBuildInfo(Map<String, String> info) {
    if (instance != null) {
      throw new IllegalStateException("setBuildInfo called twice.");
    }
    instance = new BlazeVersionInfo(info);
    logVersionInfo(instance);
  }

  public boolean isAvailable() {
    return !buildData.isEmpty();
  }

  public String getSummary() {
    if (buildData.isEmpty()) {
      return null;
    }
    return StringUtilities.layoutTable(buildData);
  }

  public boolean isReleasedBlaze() {
    String buildLabel = buildData.get(BUILD_LABEL);
    return buildLabel != null && buildLabel.length() > 0;
  }

  public String getReleaseName() {
    String buildLabel = buildData.get(BUILD_LABEL);
    return (buildLabel != null && buildLabel.length() > 0)
        ? "release " + buildLabel
        : "development version";
  }

  public String getVersion() {
    String buildLabel = buildData.get(BUILD_LABEL);
    return buildLabel != null ? buildLabel : "";
  }

  public long getTimestamp() {
    String timestamp = buildData.get(BUILD_TIMESTAMP);
    if (timestamp == null || timestamp.equals("0")) {
      return new Date().getTime();
    }
    return Long.parseLong(timestamp);
  }

  @VisibleForTesting
  public Map<String, String> getBuildData() {
    return buildData;
  }
}
