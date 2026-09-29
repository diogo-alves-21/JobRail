/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import groovy.transform.Trait;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jobrail.core.Job;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Failed;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
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

    @Test
    @TestTransaction
    void jobsList_process_updateJobEntity() {
        Job emailJob = createJob("email", Pending.provider());
        Job notJob = createJob("notification", Pending.provider());
        jobPersistenceAdapter.store(emailJob);
        jobPersistenceAdapter.store(notJob);
        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Pending.provider());
        List<Job> processedJobs = jobPersistenceAdapter.process(jobs);
        assertEquals(processedJobs.getFirst().status(), Processing.provider());
        assertEquals(processedJobs.get(1).status(), Processing.provider());
    }

    @Test
    @TestTransaction
    void jobsListWithDifferentStatus_process_returnEmptyList() {
        Job emailJob = createJob("email", Failed.provider());
        jobPersistenceAdapter.store(emailJob);
        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Pending.provider());
        List<Job> processedJobs = jobPersistenceAdapter.process(jobs);
        assertTrue(processedJobs.isEmpty());
    }

    @Test
    @TestTransaction
    void emptyJobList_process_returnEmptyList() {
        List<Job> processedJobs = jobPersistenceAdapter.process(List.of());
        assertTrue(processedJobs.isEmpty());
    }

    private Job createJob(String type, JobStatus status) {
        Instant now = Instant.now();
        return new Job(null, type, "{}", status, 0, 10, now, now, now);
    }
}
