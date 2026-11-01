/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jobrail.core.exceptions.InvalidJobStatusException;
import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.jobrail.core.jobstatuses.Succeeded;

public final class JobStatusRegistry {

    private static final List<JobStatus> allStatuses = List.of(Pending.getInstance(), Processing.getInstance(),
            Succeeded.getInstance(), Dead.getInstance());

    private static final Map<String, JobStatus> statusMap = allStatuses.stream()
            .collect(Collectors.toUnmodifiableMap(JobStatus::getName, Function.identity()));

    public static List<JobStatus> all() {
        return allStatuses;
    }

    public static JobStatus fromName(String name) {
        if (name == null) {
            throw new InvalidJobStatusException(null);
        }

        return Optional.ofNullable(statusMap.get(name)).orElseThrow(() -> new InvalidJobStatusException(name));
    }
}
