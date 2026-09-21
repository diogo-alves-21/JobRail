package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.infrastructure.JobPersistenceAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;

@Tag("unit")
@QuarkusTest
public class JobQueueTest {

    @Inject
    JobQueue jobQueue;

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Test
    void pendingJob_enqueueJob_persistJob(){
        Instant before = Instant.now();
        UUID jobId = jobQueue.enqueue("email", "{}", 3);
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
}
