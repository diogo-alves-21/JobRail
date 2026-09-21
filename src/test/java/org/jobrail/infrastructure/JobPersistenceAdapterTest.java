/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jobrail.core.Job;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag("unit")
@QuarkusTest
public class JobPersistenceAdapterTest {

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Test
    @TestTransaction
    void validEntityId_findByIdOptional_returnJob() {
        Job job = createJob("email", Pending.provider());
        UUID jobId = jobPersistenceAdapter.store(job).id();

        Optional<Job> result = jobPersistenceAdapter.findByIdOptional(jobId);
        assertTrue(result.isPresent());
    }

    @Test
    @TestTransaction
    void validEntityId_findByIdOptional_returnEmpty() {
        Optional<Job> result = jobPersistenceAdapter.findByIdOptional(UUID.randomUUID());
        assertFalse(result.isPresent());
    }

    @Test
    @TestTransaction
    void jobStatus_listJobsByStatus_returnJobList() {
        jobPersistenceAdapter.store(createJob("email", Pending.provider()));
        jobPersistenceAdapter.store(createJob("notification", Pending.provider()));

        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Pending.provider());
        assertEquals(2, jobs.size());
    }

    @Test
    @TestTransaction
    void jobStatus_listJobsByStatus_returnEmptyList() {
        jobPersistenceAdapter.store(createJob("email", Pending.provider()));
        jobPersistenceAdapter.store(createJob("notification", Pending.provider()));

        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Processing.provider());
        assertTrue(jobs.isEmpty());
    }

    private Job createJob(String type, JobStatus status) {
        Instant now = Instant.now();
        return new Job(null, type, "{}", status, 0, 10, now, now, now);
    }
}
