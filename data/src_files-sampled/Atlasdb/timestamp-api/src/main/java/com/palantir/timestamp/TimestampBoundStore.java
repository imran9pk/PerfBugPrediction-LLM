package com.palantir.timestamp;

import com.palantir.processors.AutoDelegate;

@AutoDelegate
public interface TimestampBoundStore {
    long getUpperLimit();

    void storeUpperLimit(long limit) throws MultipleRunningTimestampServiceError;
}
