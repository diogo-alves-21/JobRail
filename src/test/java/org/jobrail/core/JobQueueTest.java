/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jobrail.core.exceptions.QueueJobException;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.infrastructure.JobPersistenceAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Tag("unit")
@QuarkusTest
public class JobQueueTest {

    @Inject
    JobQueue jobQueue;

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Test
    void pendingJob_queueJob_persistJob() {
        Instant before = Instant.now();
        UUID jobId = jobQueue.queue("email", "{}", 3);
        Instant after = Instant.now();
        Optional<Job> job = jobPersistenceAdapter.findByIdOptional(jobId);
        assertTrue(job.isPresent());
        assertEquals(jobId, job.get().id());
        assertEquals("email", job.get().type());
        assertEquals("{}", job.get().payload());
        assertEquals(3, job.get().maxAttempts());
        assertEquals(0, job.get().currentAttempts());
        assertFalse(job.get().runAfter().isBefore(before));
        assertFalse(job.get().runAfter().isAfter(after));
        assertEquals(Pending.provider(), job.get().status());
    }

    @Test
    void emptyType_queueJob_throwsException() {
        QueueJobException exception = assertThrows(QueueJobException.class, () -> jobQueue.queue("", "{}", 3));
        assertEquals("Job type can't be empty", exception.getMessage());
    }

    @Test
    void emptyPayload_queueJob_throwsException() {
        QueueJobException exception = assertThrows(QueueJobException.class, () -> jobQueue.queue("email", "", 3));
        assertEquals("Job payload can't be empty", exception.getMessage());
    }

    @Test
    void maxAttemptsSmallerThanOne_queueJob_throwsException() {
        QueueJobException exception = assertThrows(QueueJobException.class, () -> jobQueue.queue("email", "{}", 0));
        assertEquals("Job must run at least 1 time", exception.getMessage());
    }
}
