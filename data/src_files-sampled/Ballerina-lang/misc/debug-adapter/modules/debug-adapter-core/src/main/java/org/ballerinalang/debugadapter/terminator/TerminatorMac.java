package org.ballerinalang.debugadapter.terminator;

import org.apache.commons.compress.utils.IOUtils;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class TerminatorMac extends TerminatorUnix {
    private String[] getFindProcessCommand(String script) {
        String[] cmd = {
                "/bin/sh", "-c", "ps ax | grep " + script + " | grep -v 'grep' | awk '{print $1}'"
        };
        return cmd;
    }

    public void terminate() {
        int processID;
        String[] findProcessCommand = getFindProcessCommand(PROCESS_IDENTIFIER);
        BufferedReader reader = null;
        try {
            Process findProcess = Runtime.getRuntime().exec(findProcessCommand);
            findProcess.waitFor();
            reader = new BufferedReader(new InputStreamReader(findProcess.getInputStream(), Charset.defaultCharset()));

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    processID = Integer.parseInt(line);
                    killChildProcesses(processID);
                    kill(processID);
                } catch (Throwable e) {
}
            }
        } catch (Throwable e) {
} finally {
            if (reader != null) {
                IOUtils.closeQuietly(reader);
            }
        }
    }
}
