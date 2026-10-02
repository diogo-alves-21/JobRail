/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.jobstatuses.Pending;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
@RequiredArgsConstructor
public class JobProcessor {

    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;

    @Scheduled(every = "5s")
    @Transactional
    public void process() {

        List<Job> jobs = jobRepository.listJobsByStatus(Pending.getInstance());
        jobs = jobs.stream().filter(j -> j.runAfter().compareTo(Instant.now()) < 0).toList();
        if (jobs.isEmpty())
            return;

        List<Job> processedJobs = jobRepository.process(jobs);
        if (processedJobs.isEmpty())
            return;

        processedJobs.forEach(job -> {
            JobHandler handler = registry.getHandler(job.type().toUpperCase(Locale.ROOT));
            int attempts = handler.handleJob(job);
            if (attempts <= job.maxAttempts()) {
                jobRepository.markSucceeded(job.id(), attempts);
            } else {
                jobRepository.markFailed(job.id());
            }
        });
    }
}
