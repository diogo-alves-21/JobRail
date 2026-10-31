/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.worker;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jobrail.core.Job;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.jobrail.core.jobstatuses.Succeeded;
import org.jobrail.infrastructure.JobPersistenceAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@QuarkusTest
@TestProfile(WorkersEnabledProfile.class)
class WorkerPoolIntegrationTest {

    private static final int JOBS = 12;

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery("DELETE FROM JobEntity").executeUpdate());
        RecordingTestHandler.reset();
    }

    @Test
    void startedWorkers_drainTheQueue_handlingEveryJobExactlyOnce() throws InterruptedException {
        Set<UUID> ids = new HashSet<>();
        Instant created = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < JOBS; i++) {
            Job stored = jobPersistenceAdapter
                    .store(new Job(null, "testrecord", "{}", Pending.getInstance(), 0, 3, created, created, created));
            ids.add(stored.id());
        }

        waitUntilNothingIsPendingOrProcessing();

        assertEquals(JOBS, jobPersistenceAdapter.listJobsByStatus(Succeeded.getInstance()).size());
        assertEquals(ids, RecordingTestHandler.CALLS.keySet());
        RecordingTestHandler.CALLS.forEach((id, calls) -> assertEquals(1, calls.get(), "job " + id + " ran twice"));
    }

    private void waitUntilNothingIsPendingOrProcessing() throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            boolean drained = jobPersistenceAdapter.listJobsByStatus(Pending.getInstance()).isEmpty()
                    && jobPersistenceAdapter.listJobsByStatus(Processing.getInstance()).isEmpty();
            if (drained) {
                return;
            }
            Thread.sleep(100);
        }
        fail("the worker pool did not drain the queue within 30s");
    }
}
