package com.google.devtools.build.lib.runtime;

import com.google.devtools.build.lib.util.ExitCode;

@Deprecated
public class CommandPrecompleteEvent {
  private final ExitCode exitCode;

  public CommandPrecompleteEvent(ExitCode exitCode) {
    this.exitCode = exitCode;
  }

  public ExitCode getExitCode() {
    return exitCode;
  }
}
