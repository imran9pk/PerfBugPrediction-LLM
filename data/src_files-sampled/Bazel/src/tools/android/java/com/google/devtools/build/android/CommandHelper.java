package com.google.devtools.build.android;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

class CommandHelper {
  static String execute(String action, List<String> command) throws IOException {
    final StringBuilder processLog = new StringBuilder();

    final Process process = new ProcessBuilder().command(command).redirectErrorStream(true).start();
    processLog.append("Command: ");
    Joiner.on("\\\n\t").appendTo(processLog, command);
    processLog.append("\nOutput:\n");
    final InputStreamReader stdout = new InputStreamReader(process.getInputStream(), UTF_8);
    while (process.isAlive()) {
      processLog.append(CharStreams.toString(stdout));
    }
    while (stdout.ready()) {
      processLog.append(CharStreams.toString(stdout));
    }
    if (process.exitValue() != 0) {
      throw new RuntimeException(String.format("Error during %s:", action) + "\n" + processLog);
    }
    return processLog.toString();
  }

  private CommandHelper() {}
}
