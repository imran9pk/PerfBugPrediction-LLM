package com.palantir.atlasdb.restore;

import com.palantir.logsafe.Arg;

public interface OutputStateLogger {
    void info(String message, Arg<?>... args);

    void warn(String message, Arg<?>... args);

    void error(String message, Arg<?>... args);
}
