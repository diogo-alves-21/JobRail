/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.worker;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jobrail.core.JobProcessor;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@ApplicationScoped
@RequiredArgsConstructor
@Log
public class WorkerScheduler {

    private final JobProcessor jobProcessor;

    @ConfigProperty(name = "jobrail.workers.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "jobrail.workers.count", defaultValue = "4")
    int workerCount;

    @ConfigProperty(name = "jobrail.workers.poll-interval", defaultValue = "1s")
    Duration pollInterval;

    private volatile boolean running;
    private ExecutorService workers;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        running = true;
        workers = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workLoop);
        }
        log.log(Level.INFO, "Started %d workers (poll interval %s)".formatted(workerCount, pollInterval));
    }

    void onStop(@Observes ShutdownEvent event) throws InterruptedException {
        running = false;
        if (workers != null) {
            workers.shutdown();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Scheduled(every = "{jobrail.workers.reclaim-interval:30s}")
    void reclaim() {
        if (running) {
            jobProcessor.reclaimStuckJobs();
        }
    }

    private void workLoop() {
        while (running) {
            try {
                if (!jobProcessor.claimAndRunOnce()) {
                    Thread.sleep(pollInterval.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.log(Level.WARNING, "Worker iteration failed; continuing", e);
            }
        }
    }
}
