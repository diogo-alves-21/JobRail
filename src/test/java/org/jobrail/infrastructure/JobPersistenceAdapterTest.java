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
import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.jobrail.core.jobstatuses.Succeeded;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
        Job job = createJob("email", Pending.getInstance());
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
        jobPersistenceAdapter.store(createJob("email", Pending.getInstance()));
        jobPersistenceAdapter.store(createJob("notification", Pending.getInstance()));

        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Pending.getInstance());
        assertEquals(2, jobs.size());
    }

    @Test
    @TestTransaction
    void jobStatus_listJobsByStatus_returnEmptyList() {
        jobPersistenceAdapter.store(createJob("email", Pending.getInstance()));
        jobPersistenceAdapter.store(createJob("notification", Pending.getInstance()));

        List<Job> jobs = jobPersistenceAdapter.listJobsByStatus(Processing.getInstance());
        assertTrue(jobs.isEmpty());
    }

    @Test
    @TestTransaction
    void severalDueJobs_claimNext_claimsOnlyTheOldestOne() {
        Job oldest = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(300)));
        Job middle = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(200)));
        Job newest = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(100)));

        List<Job> claimed = jobPersistenceAdapter.claimNext();

        assertEquals(1, claimed.size());
        assertEquals(oldest.id(), claimed.getFirst().id());
        assertEquals(Processing.getInstance(), claimed.getFirst().status());
        List<UUID> stillPending = jobPersistenceAdapter.listJobsByStatus(Pending.getInstance()).stream().map(Job::id)
                .toList();
        assertEquals(2, stillPending.size());
        assertTrue(stillPending.containsAll(List.of(middle.id(), newest.id())));
    }

    @Test
    @TestTransaction
    void claimedJob_claimNext_refreshesUpdatedAt() {
        Instant old = secondsAgo(120);
        Job stale = jobPersistenceAdapter
                .store(new Job(null, "email", "{}", Pending.getInstance(), 0, 3, old, old, old));

        Job claimed = jobPersistenceAdapter.claimNext().getFirst();

        assertEquals(stale.id(), claimed.id());
        assertTrue(claimed.updatedAt().isAfter(old.plusSeconds(60)));
    }

    @Test
    @TestTransaction
    void calledRepeatedly_claimNext_handsOutEachJobOnceThenRunsDry() {
        Job first = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(200)));
        Job second = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(100)));

        assertEquals(first.id(), jobPersistenceAdapter.claimNext().getFirst().id());
        assertEquals(second.id(), jobPersistenceAdapter.claimNext().getFirst().id());
        assertTrue(jobPersistenceAdapter.claimNext().isEmpty());
    }

    @Test
    @TestTransaction
    void noJobs_claimNext_returnsEmpty() {
        assertTrue(jobPersistenceAdapter.claimNext().isEmpty());
    }

    @Test
    @TestTransaction
    void onlyNotYetDueJob_claimNext_returnsEmptyAndLeavesItPending() {
        Job future = jobPersistenceAdapter
                .store(jobDueAt(Pending.getInstance(), Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS)));

        assertTrue(jobPersistenceAdapter.claimNext().isEmpty());

        assertEquals(Pending.getInstance(), jobPersistenceAdapter.findByIdOptional(future.id()).orElseThrow().status());
    }

    @Test
    @TestTransaction
    void dueJobsInOtherStatuses_claimNext_returnsEmpty() {
        jobPersistenceAdapter.store(jobDueAt(Dead.getInstance(), secondsAgo(100)));
        jobPersistenceAdapter.store(jobDueAt(Succeeded.getInstance(), secondsAgo(100)));
        jobPersistenceAdapter.store(jobDueAt(Processing.getInstance(), secondsAgo(100)));

        assertTrue(jobPersistenceAdapter.claimNext().isEmpty());
    }

    @Test
    @TestTransaction
    void dueJobBehindNotYetDueJob_claimNext_skipsTheNotYetDueOne() {
        jobPersistenceAdapter
                .store(jobDueAt(Pending.getInstance(), Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS)));
        Job due = jobPersistenceAdapter.store(jobDueAt(Pending.getInstance(), secondsAgo(10)));

        List<Job> claimed = jobPersistenceAdapter.claimNext();

        assertEquals(1, claimed.size());
        assertEquals(due.id(), claimed.getFirst().id());
    }

    @Test
    @TestTransaction
    void claimedJob_updateAttempts_refreshesLeaseTimestamp() {
        Instant old = Instant.now().minusSeconds(120);
        Job staleJob = new Job(null, "email", "{}", Processing.getInstance(), 0, 10, old, old, old);
        UUID id = jobPersistenceAdapter.store(staleJob).id();

        jobPersistenceAdapter.updateAttempts(id);

        Job result = jobPersistenceAdapter.findByIdOptional(id).orElseThrow();
        assertEquals(1, result.currentAttempts());
        assertTrue(result.updatedAt().isAfter(old.plusSeconds(60)));
    }

    @Test
    @TestTransaction
    void jobsInSeveralStatuses_countJobsByStatus_groupsAndCountsThem() {
        Map<JobStatus, Long> before = jobPersistenceAdapter.countJobsByStatus();
        jobPersistenceAdapter.store(createJob("email", Pending.getInstance()));
        jobPersistenceAdapter.store(createJob("email", Pending.getInstance()));
        jobPersistenceAdapter.store(createJob("email", Pending.getInstance()));
        jobPersistenceAdapter.store(createJob("email", Dead.getInstance()));
        jobPersistenceAdapter.store(createJob("email", Succeeded.getInstance()));
        jobPersistenceAdapter.store(createJob("email", Succeeded.getInstance()));

        Map<JobStatus, Long> after = jobPersistenceAdapter.countJobsByStatus();

        assertEquals(3L,
                after.getOrDefault(Pending.getInstance(), 0L) - before.getOrDefault(Pending.getInstance(), 0L));
        assertEquals(1L, after.getOrDefault(Dead.getInstance(), 0L) - before.getOrDefault(Dead.getInstance(), 0L));
        assertEquals(2L,
                after.getOrDefault(Succeeded.getInstance(), 0L) - before.getOrDefault(Succeeded.getInstance(), 0L));
        assertEquals(before.getOrDefault(Processing.getInstance(), 0L),
                after.getOrDefault(Processing.getInstance(), 0L));
    }

    @Test
    @TestTransaction
    void statusWithNoJobs_countJobsByStatus_isAbsentFromTheMap() {
        // zero-filling is the query's job (GetJobsStats); the repository reports only what exists
        Map<JobStatus, Long> counts = jobPersistenceAdapter.countJobsByStatus();
        counts.values().forEach(count -> assertTrue(count > 0));
    }

    private Job createJob(String type, JobStatus status) {
        Instant now = Instant.now();
        return new Job(null, type, "{}", status, 0, 10, now, now, now);
    }

    private Job jobDueAt(JobStatus status, Instant runAfter) {
        Instant created = secondsAgo(600);
        return new Job(null, "email", "{}", status, 0, 3, runAfter, created, created);
    }

    private static Instant secondsAgo(long seconds) {
        return Instant.now().minusSeconds(seconds).truncatedTo(ChronoUnit.MICROS);
    }
}
