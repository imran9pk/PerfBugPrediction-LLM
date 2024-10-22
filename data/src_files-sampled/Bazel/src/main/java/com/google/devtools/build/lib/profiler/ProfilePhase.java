package com.google.devtools.build.lib.profiler;

public enum ProfilePhase {
  LAUNCH("launch", "Launch Blaze"),
  INIT("init", "Initialize command"),
  TARGET_PATTERN_EVAL("target pattern evaluation", "Evaluate target patterns"),
  ANALYZE("interleaved loading-and-analysis", "Load and analyze dependencies"),
  LICENSE("license checking", "Analyze licenses"),
  PREPARE("preparation", "Prepare for build"),
  EXECUTE("execution", "Build artifacts"),
  FINISH("finish", "Complete build"),
  UNKNOWN("unknown", "unknown");

  public final String nick;
  public final String description;

  ProfilePhase(String nick, String description) {
    this.nick = nick;
    this.description = description;
  }

  public static ProfilePhase getPhaseFromDescription(String description) {
    for (ProfilePhase profilePhase : ProfilePhase.values()) {
      if (profilePhase.description.equals(description)) {
        return profilePhase;
      }
    }
    return UNKNOWN;
  }
}
