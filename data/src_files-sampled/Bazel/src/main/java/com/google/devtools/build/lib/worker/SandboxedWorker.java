package com.google.devtools.build.lib.worker;

import com.google.common.flogger.GoogleLogger;
import com.google.devtools.build.lib.sandbox.SandboxHelpers.SandboxInputs;
import com.google.devtools.build.lib.sandbox.SandboxHelpers.SandboxOutputs;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.Set;

final class SandboxedWorker extends SingleplexWorker {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private final WorkerExecRoot workerExecRoot;

  SandboxedWorker(WorkerKey workerKey, int workerId, Path workDir, Path logFile) {
    super(workerKey, workerId, workDir, logFile);
    workerExecRoot = new WorkerExecRoot(workDir);
  }

  @Override
  public boolean isSandboxed() {
    return true;
  }

  @Override
  public void prepareExecution(
      SandboxInputs inputFiles, SandboxOutputs outputs, Set<PathFragment> workerFiles)
      throws IOException {
    workerExecRoot.createFileSystem(workerFiles, inputFiles, outputs);

    super.prepareExecution(inputFiles, outputs, workerFiles);
  }

  @Override
  public void finishExecution(Path execRoot, SandboxOutputs outputs) throws IOException {
    super.finishExecution(execRoot, outputs);

    workerExecRoot.copyOutputs(execRoot, outputs);
  }

  @Override
  void destroy() {
    super.destroy();
    try {
      workDir.deleteTree();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Caught IOException while deleting workdir.");
    }
  }
}
