/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.exceptions.HandleJobException;
import org.jobrail.core.jobstatuses.Pending;
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

    @Scheduled(every = "5s")
    public void process() {

        jobRepository.listJobsByStatus(Processing.getInstance()).stream().filter(this::filterStuckJobs).toList()
                .forEach(this::reclaimStuckJob);

        List<Job> pendingJobs = jobRepository.listJobsByStatus(Pending.getInstance()).stream()
                .filter(j -> j.runAfter().compareTo(Instant.now()) < 0).toList();
        if (pendingJobs.isEmpty())
            return;

        List<Job> claimedJobs = jobRepository.claim(pendingJobs);
        if (claimedJobs.isEmpty())
            return;

        handleJobs(claimedJobs);
    }

    private boolean filterStuckJobs(Job j) {
        Duration difference = Duration.between(j.updatedAt(), Instant.now());
        return difference.getSeconds() > TIMEOUT_IN_SECONDS;
    }

    private void reclaimStuckJob(Job job) {
        int nextAttempt = job.currentAttempts() + 1;
        if (nextAttempt <= job.maxAttempts()) {
            jobRepository.rescheduleIfStuck(job.id(), job.updatedAt(),
                    Instant.now().plus(Backoff.runAfter(nextAttempt)));
        } else {
            jobRepository.markDeadIfStuck(job.id(), job.updatedAt());
        }
    }

    private void handleJobs(List<Job> claimedJobs) {
        claimedJobs.forEach(this::handleEachJob);
    }

    private void handleEachJob(Job job) {
        int attempt = jobRepository.updateAttempts(job.id());
        try {
            JobHandler handler = registry.getHandler(job.type().toUpperCase(Locale.ROOT));
            handler.handleJob(job);
            jobRepository.markSucceeded(job.id());
        } catch (HandleJobException _) {
            handleFailedJob(job, attempt);
        }
    }

    private void handleFailedJob(Job job, int attempt) {
        if (attempt < job.maxAttempts()) {
            Instant runAfter = Instant.now().plus(Backoff.runAfter(attempt));
            jobRepository.reschedule(job.id(), runAfter);
        } else {
            jobRepository.markDead(job.id());
        }
    }
}
