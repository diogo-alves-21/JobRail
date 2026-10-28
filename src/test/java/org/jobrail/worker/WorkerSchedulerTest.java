/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.jobrail.core.JobProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Tag("unit")
class WorkerSchedulerTest {

    private final JobProcessor processor = mock(JobProcessor.class);
    private WorkerScheduler scheduler;

    @AfterEach
    void stopWorkers() throws InterruptedException {
        if (scheduler != null) {
            scheduler.onStop(null);
        }
    }

    @Test
    void disabled_onStart_startsNoWorkers() throws InterruptedException {
        scheduler = scheduler(false, 2, Duration.ofMillis(50));

        scheduler.onStart(null);
        Thread.sleep(300);

        verifyNoInteractions(processor);
    }

    @Test
    void configuredCount_onStart_startsThatManyWorkersAtTheSameTime() throws InterruptedException {
        CountDownLatch allArrived = new CountDownLatch(3);
        when(processor.claimAndRunOnce()).thenAnswer(invocation -> {
            allArrived.countDown();
            allArrived.await(2, TimeUnit.SECONDS); // only returns early if three workers are inside at once
            return false;
        });
        scheduler = scheduler(true, 3, Duration.ofMillis(50));

        scheduler.onStart(null);

        assertTrue(allArrived.await(2, TimeUnit.SECONDS), "expected 3 workers calling claimAndRunOnce concurrently");
    }

    @Test
    void workerThatFoundWork_doesNotSleepBeforeAskingAgain() {
        // poll interval of 2s: if the worker slept after each job, only one call would happen within the 1s window
        when(processor.claimAndRunOnce()).thenReturn(true, true, true, true, true, false);
        scheduler = scheduler(true, 1, Duration.ofSeconds(2));

        scheduler.onStart(null);

        verify(processor, timeout(1000).atLeast(6)).claimAndRunOnce();
    }

    @Test
    void idleWorker_sleepsBetweenPolls() throws InterruptedException {
        when(processor.claimAndRunOnce()).thenReturn(false);
        scheduler = scheduler(true, 1, Duration.ofMillis(300));

        scheduler.onStart(null);
        Thread.sleep(1000);

        // ~4 polls fit in a second (at 0, 300, 600, 900 ms); a worker that never slept would make thousands
        verify(processor, atLeast(2)).claimAndRunOnce();
        verify(processor, atMost(6)).claimAndRunOnce();
    }

    @Test
    void iterationThatThrows_workerKeepsRunning() {
        when(processor.claimAndRunOnce()).thenThrow(new RuntimeException("boom")).thenReturn(true, true, false);
        scheduler = scheduler(true, 1, Duration.ofMillis(100));

        scheduler.onStart(null);

        verify(processor, timeout(2000).atLeast(4)).claimAndRunOnce();
    }

    @Test
    void stopped_workers_noLongerAskForWork() throws InterruptedException {
        when(processor.claimAndRunOnce()).thenReturn(false);
        scheduler = scheduler(true, 2, Duration.ofMillis(50));
        scheduler.onStart(null);
        Thread.sleep(200);

        scheduler.onStop(null);
        int callsAtStop = mockingDetails(processor).getInvocations().size();
        Thread.sleep(300);

        assertEquals(callsAtStop, mockingDetails(processor).getInvocations().size());
    }

    @Test
    void running_reclaim_asksTheProcessorToGiveBackStuckJobs() {
        when(processor.claimAndRunOnce()).thenReturn(false);
        scheduler = scheduler(true, 1, Duration.ofSeconds(1));
        scheduler.onStart(null);

        scheduler.reclaim();

        verify(processor, times(1)).reclaimStuckJobs();
    }

    @Test
    void notRunning_reclaim_doesNothing() {
        scheduler = scheduler(false, 1, Duration.ofSeconds(1));
        scheduler.onStart(null);

        scheduler.reclaim();

        verifyNoInteractions(processor);
    }

    private WorkerScheduler scheduler(boolean enabled, int workerCount, Duration pollInterval) {
        WorkerScheduler created = new WorkerScheduler(processor);
        created.enabled = enabled;
        created.workerCount = workerCount;
        created.pollInterval = pollInterval;
        return created;
    }
}
