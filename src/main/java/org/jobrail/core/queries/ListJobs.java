/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.queries;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.Job;
import org.jobrail.core.JobRepository;
import org.jobrail.core.JobStatusRegistry;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
@RequiredArgsConstructor
public class ListJobs {

    private final JobRepository jobRepository;

    public List<Job> query(String status) {

        if (!nonBlank(status)) {
            return jobRepository.listJobsByStatus(null);
        }
        return jobRepository.listJobsByStatus(JobStatusRegistry.fromName(status.toUpperCase(Locale.ROOT)));
    }

    private boolean nonBlank(String val) {

        return val != null && !val.isBlank();
    }
}
