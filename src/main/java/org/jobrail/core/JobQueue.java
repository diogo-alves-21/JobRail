/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.exceptions.QueueJobException;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobQueue {

    private final JobRepository jobRepository;

    public UUID queue(String type, String payload, int maxAttempts) {
        checkType(type);
        checkPayload(payload);
        checkMaxAttempts(maxAttempts);
        Job job = jobRepository.store(Job.newPending(type, payload, maxAttempts));
        return job.id();
    }

    private void checkType(String type) {
        if (type == null || type.isBlank())
            throwQueueJobException("Job type can't be empty");
    }

    private void checkPayload(String payload) {
        if (payload == null || payload.isBlank())
            throwQueueJobException("Job payload can't be empty");
    }

    private void checkMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0)
            throwQueueJobException("Job must run at least 1 time");
    }

    private void throwQueueJobException(String message) {
        throw new QueueJobException(message);
    }
}
