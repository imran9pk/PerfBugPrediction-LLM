package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.time.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ThrottlingOutputEventListener implements OutputEventListener {
    private final OutputEventListener listener;

    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final int throttleMs;
    private final Object lock = new Object();

    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();

    public ThrottlingOutputEventListener(OutputEventListener listener, Clock clock) {
        this(listener, Integer.getInteger("org.gradle.internal.console.throttle", 100), Executors.newSingleThreadScheduledExecutor(), clock);
    }

    ThrottlingOutputEventListener(OutputEventListener listener, int throttleMs, ScheduledExecutorService executor, Clock clock) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.executor = executor;
        this.clock = clock;
        scheduleUpdateNow();
    }

    private void scheduleUpdateNow() {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                onOutput(new UpdateNowEvent(clock.getCurrentTime()));
            }
        }, throttleMs, throttleMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOutput(OutputEvent newEvent) {
        synchronized (lock) {
            queue.add(newEvent);

            if (queue.size() == 10000 || newEvent instanceof UpdateNowEvent) {
                renderNow();
                return;
            }

            if (newEvent instanceof FlushOutputEvent) {
                renderNow();
                return;
            }

            if (newEvent instanceof EndOutputEvent) {
                renderNow();
                executor.shutdown();
            }

            }
    }

    private void renderNow() {
        while (!queue.isEmpty()) {
            OutputEvent event = queue.remove(0);
            listener.onOutput(event);
        }
    }
}
