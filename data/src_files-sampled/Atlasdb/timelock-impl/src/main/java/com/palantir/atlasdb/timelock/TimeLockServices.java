package com.palantir.atlasdb.timelock;

import com.palantir.lock.LockService;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.timestamp.TimestampManagementService;
import com.palantir.timestamp.TimestampService;
import java.util.stream.Stream;
import org.immutables.value.Value;

@Value.Immutable
public interface TimeLockServices extends AutoCloseable {
    SafeLogger log = SafeLoggerFactory.get(TimeLockServices.class);

    static TimeLockServices create(
            TimestampService timestampService,
            LockService lockService,
            AsyncTimelockService timelockService,
            AsyncTimelockResource timelockResource,
            TimestampManagementService timestampManagementService) {
        return ImmutableTimeLockServices.builder()
                .timestampService(timestampService)
                .lockService(lockService)
                .timestampManagementService(timestampManagementService)
                .timelockService(timelockService)
                .timelockResource(timelockResource)
                .build();
    }

    TimestampService getTimestampService();

    LockService getLockService();
    AsyncTimelockResource getTimelockResource();
    AsyncTimelockService getTimelockService();

    TimestampManagementService getTimestampManagementService();

    @Override
    default void close() {
        Stream.of(
                        getTimestampService(),
                        getLockService(),
                        getTimelockResource(),
                        getTimelockService(),
                        getTimestampManagementService())
                .filter(service -> service instanceof AutoCloseable)
                .distinct()
                .forEach(service -> {
                    try {
                        ((AutoCloseable) service).close();
                    } catch (Exception e) {
                        log.info("Exception occurred when closing a constituent of TimeLockServices", e);
                    }
                });
    }
}
