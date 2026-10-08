/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.jobrail.core.exceptions.QueueJobException;
import org.jobrail.core.jobstatuses.Pending;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

@Tag("unit")
public class JobQueueTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobQueue jobQueue = new JobQueue(jobRepository);

    @Test
    void pendingJob_queueJob_persistJob() {
        UUID storedId = UUID.randomUUID();
        when(jobRepository.store(any()))
                .thenReturn(new Job(storedId, "email", "{}", Pending.getInstance(), 0, 3, Instant.now(), Instant.now(), Instant.now()));

        Instant before = Instant.now();
        UUID result = jobQueue.queue("email", "{}", 3);
        Instant after = Instant.now();

        assertEquals(storedId, result);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).store(captor.capture());
        Job passed = captor.getValue();
        assertEquals("email", passed.type());
        assertEquals("{}", passed.payload());
        assertEquals(3, passed.maxAttempts());
        assertEquals(0, passed.currentAttempts());
        assertFalse(passed.runAfter().isBefore(before));
        assertFalse(passed.runAfter().isAfter(after));
        assertEquals(Pending.getInstance(), passed.status());
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

    @Test
    void emptyType_queue_throwsAndNeverStores() {
        QueueJobException exception = assertThrows(QueueJobException.class, () -> jobQueue.queue("", "{}", 3));
        assertEquals("Job type can't be empty", exception.getMessage());
        verifyNoInteractions(jobRepository);
    }
}
