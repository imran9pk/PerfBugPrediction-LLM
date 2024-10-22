package com.google.devtools.build.lib.skyframe;

import java.util.concurrent.atomic.AtomicInteger;

public class ConfiguredTargetProgressReceiver {

  private AtomicInteger configuredTargetsCompleted = new AtomicInteger();

  void doneConfigureTarget() {
    configuredTargetsCompleted.incrementAndGet();
  }

  public void reset() {
    configuredTargetsCompleted.set(0);
  }

  public String getProgressString() {
    String progress = "" + configuredTargetsCompleted + " ";
    progress += (configuredTargetsCompleted.get() != 1) ? "targets" : "target";
    progress += " configured";
    return progress;
  }
}
