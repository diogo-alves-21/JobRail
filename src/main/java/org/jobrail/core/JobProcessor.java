/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.exceptions.HandleJobException;
import org.jobrail.core.jobstatuses.Processing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
@RequiredArgsConstructor
public class JobProcessor {

    private static final int TIMEOUT_IN_SECONDS = 60;
    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;

    public boolean claimAndRunOnce() {
        List<Job> claimedJobs = jobRepository.claimNext();
        if (claimedJobs.isEmpty())
            return false;

        handleEachJob(claimedJobs.getFirst());
        return true;
    }

    public void reclaimStuckJobs() {
        jobRepository.listJobsByStatus(Processing.getInstance()).stream().filter(this::filterStuckJobs).toList()
                .forEach(this::reclaimStuckJob);
    }

    private boolean filterStuckJobs(Job j) {
        Duration difference = Duration.between(j.updatedAt(), Instant.now());
        return difference.getSeconds() > TIMEOUT_IN_SECONDS;
    }

    private void reclaimStuckJob(Job job) {
        int nextAttempt = job.currentAttempts() + 1;
        if (nextAttempt <= job.maxAttempts()) {
            jobRepository.reschedule(job.id(), job.updatedAt(), Instant.now().plus(Backoff.runAfter(nextAttempt)));
        } else {
            jobRepository.markDead(job.id(), job.updatedAt());
        }
    }

    private void handleEachJob(Job claimedJob) {
        Job job = jobRepository.updateAttempts(claimedJob.id());
        try {
            JobHandler handler = registry.getHandler(job.type().toUpperCase(Locale.ROOT));
            if (handler == null) {
                jobRepository.markDead(job.id(), job.updatedAt());
                return;
            }
            handler.handleJob(job);
            jobRepository.markSucceeded(job.id());
        } catch (HandleJobException _) {
            handleFailedJob(job);
        }
    }

    private void handleFailedJob(Job job) {
        if (job.currentAttempts() < job.maxAttempts()) {
            Instant runAfter = Instant.now().plus(Backoff.runAfter(job.currentAttempts()));
            jobRepository.reschedule(job.id(), job.updatedAt(), runAfter);
        } else {
            jobRepository.markDead(job.id(), job.updatedAt());
        }
    }
}
