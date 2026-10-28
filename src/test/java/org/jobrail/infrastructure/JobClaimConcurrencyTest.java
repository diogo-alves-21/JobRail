/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jobrail.core.Job;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The evidence that SKIP LOCKED claiming works: several workers drain the table at the same time, every due job is
 * claimed exactly once, and jobs that are not due yet are never claimed. Each claimNext() call runs in its own
 * transaction on its own thread, so this cannot run inside @TestTransaction.
 */
@Tag("integration")
@QuarkusTest
class JobClaimConcurrencyTest {

    private static final int DUE_JOBS = 40;
    private static final int NOT_DUE_JOBS = 5;
    private static final int WORKERS = 6;

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanTable() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery("DELETE FROM JobEntity").executeUpdate());
    }

    @Test
    void severalWorkers_claimNext_claimEveryDueJobExactlyOnce() throws Exception {
        Set<UUID> dueIds = new HashSet<>();
        for (int i = 0; i < DUE_JOBS; i++) {
            dueIds.add(jobPersistenceAdapter.store(job(Instant.now().minusSeconds(100 - i))).id());
        }
        Set<UUID> notDueIds = new HashSet<>();
        for (int i = 0; i < NOT_DUE_JOBS; i++) {
            notDueIds.add(jobPersistenceAdapter.store(job(Instant.now().plusSeconds(3600))).id());
        }

        Queue<UUID> claimed = new ConcurrentLinkedQueue<>();
        CountDownLatch go = new CountDownLatch(1);
        Callable<Void> worker = () -> {
            go.await(); // release every worker at once to maximise contention
            while (true) {
                List<Job> got = jobPersistenceAdapter.claimNext();
                if (got.isEmpty()) {
                    return null;
                }
                got.forEach(j -> claimed.add(j.id()));
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Future<Void>> running = new ArrayList<>();
            for (int i = 0; i < WORKERS; i++) {
                running.add(pool.submit(worker));
            }
            go.countDown();
            for (Future<Void> future : running) {
                future.get(60, TimeUnit.SECONDS); // rethrows anything a worker threw
            }
        } finally {
            pool.shutdownNow();
        }

        // every due job claimed, none twice, none of the not-yet-due ones
        assertEquals(DUE_JOBS, claimed.size(), "a job was claimed more than once, or some were never claimed");
        assertEquals(dueIds, new HashSet<>(claimed));
        assertTrue(claimed.stream().noneMatch(notDueIds::contains));

        // and the database agrees
        assertEquals(DUE_JOBS, jobPersistenceAdapter.listJobsByStatus(Processing.getInstance()).size());
        assertEquals(NOT_DUE_JOBS, jobPersistenceAdapter.listJobsByStatus(Pending.getInstance()).size());
    }

    private Job job(Instant runAfter) {
        Instant created = Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MICROS);
        return new Job(null, "email", "{}", Pending.getInstance(), 0, 3, runAfter.truncatedTo(ChronoUnit.MICROS),
                created, created);
    }
}
