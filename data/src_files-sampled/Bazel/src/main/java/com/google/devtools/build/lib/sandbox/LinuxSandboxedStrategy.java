package com.google.devtools.build.lib.sandbox;

import com.google.devtools.build.lib.exec.AbstractSpawnStrategy;
import com.google.devtools.build.lib.exec.SpawnRunner;
import com.google.devtools.build.lib.exec.TreeDeleter;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import java.io.IOException;
import java.time.Duration;
import javax.annotation.Nullable;

public final class LinuxSandboxedStrategy extends AbstractSpawnStrategy {
  LinuxSandboxedStrategy(Path execRoot, SpawnRunner spawnRunner, boolean verboseFailures) {
    super(execRoot, spawnRunner, verboseFailures);
  }

  @Override
  public String toString() {
    return "linux-sandbox";
  }

  static LinuxSandboxedSpawnRunner create(
      SandboxHelpers helpers,
      CommandEnvironment cmdEnv,
      Path sandboxBase,
      Duration timeoutKillDelay,
      @Nullable SandboxfsProcess sandboxfsProcess,
      boolean sandboxfsMapSymlinkTargets,
      TreeDeleter treeDeleter)
      throws IOException {
    Path inaccessibleHelperFile = sandboxBase.getRelative("inaccessibleHelperFile");
    FileSystemUtils.touchFile(inaccessibleHelperFile);
    inaccessibleHelperFile.setReadable(false);
    inaccessibleHelperFile.setWritable(false);
    inaccessibleHelperFile.setExecutable(false);

    Path inaccessibleHelperDir = sandboxBase.getRelative("inaccessibleHelperDir");
    inaccessibleHelperDir.createDirectory();
    inaccessibleHelperDir.setReadable(false);
    inaccessibleHelperDir.setWritable(false);
    inaccessibleHelperDir.setExecutable(false);

    return new LinuxSandboxedSpawnRunner(
        helpers,
        cmdEnv,
        sandboxBase,
        inaccessibleHelperFile,
        inaccessibleHelperDir,
        timeoutKillDelay,
        sandboxfsProcess,
        sandboxfsMapSymlinkTargets,
        treeDeleter);
  }
}
