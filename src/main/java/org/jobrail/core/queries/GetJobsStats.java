/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.queries;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.JobRepository;
import org.jobrail.core.JobStatus;
import org.jobrail.core.JobStatusRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor
public class GetJobsStats {

    private final JobRepository jobRepository;

    public Map<JobStatus, Long> query() {
        Map<JobStatus, Long> counted = jobRepository.countJobsByStatus();
        Map<JobStatus, Long> stats = new LinkedHashMap<>();
        for (JobStatus status : JobStatusRegistry.all()) {
            stats.put(status, counted.getOrDefault(status, 0L));
        }
        return stats;
    }
}
