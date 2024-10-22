package com.palantir.lock.client;

import com.palantir.atlasdb.autobatch.Autobatchers;
import com.palantir.atlasdb.autobatch.BatchElement;
import com.palantir.atlasdb.autobatch.DisruptorAutobatcher;
import com.palantir.lock.v2.LockToken;
import com.palantir.lock.v2.TimelockService;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class AsyncTimeLockUnlocker implements TimeLockUnlocker, AutoCloseable {
    private static final SafeLogger log = SafeLoggerFactory.get(AsyncTimeLockUnlocker.class);
    private final DisruptorAutobatcher<Set<LockToken>, Void> autobatcher;

    private AsyncTimeLockUnlocker(DisruptorAutobatcher<Set<LockToken>, Void> autobatcher) {
        this.autobatcher = autobatcher;
    }

    public static AsyncTimeLockUnlocker create(TimelockService timelockService) {
        return new AsyncTimeLockUnlocker(Autobatchers.<Set<LockToken>, Void>independent(batch -> {
                    Set<LockToken> allTokensToUnlock = batch.stream()
                            .map(BatchElement::argument)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet());
                    try {
                        timelockService.tryUnlock(allTokensToUnlock);
                    } catch (Throwable t) {
                        log.info(
                                "Failed to unlock lock tokens {} from timelock. They will eventually expire on their "
                                        + "own, but if this message recurs frequently, it may be worth investigation.",
                                SafeArg.of("numFailed", allTokensToUnlock.size()),
                                t);
                    }
                    batch.stream().map(BatchElement::result).forEach(f -> f.set(null));
                })
                .batchFunctionTimeout(Duration.ofSeconds(30))
                .safeLoggablePurpose("async-timelock-unlocker")
                .build());
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void enqueue(Set<LockToken> tokens) {
        autobatcher.apply(tokens);
    }

    @Override
    public void close() {
        autobatcher.close();
    }
}
