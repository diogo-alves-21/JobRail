package org.jobrail.core;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Failed;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.jobrail.core.jobstatuses.Succeeded;

public final class JobStatusRegistry {

    private static final Map<String, JobStatus> statusMap =
            Stream.of(
                            Pending.getInstance(),
                            Processing.getInstance(),
                            Succeeded.getInstance(),
                            Failed.getInstance(),
                            Dead.getInstance())
                    .collect(Collectors.toUnmodifiableMap(JobStatus::getName, Function.identity()));

    public static JobStatus fromName(String name) {
        JobStatus status = statusMap.get(name);

        if (status == null) throw new IllegalArgumentException("Unknown job status: " + name);
        return status;
    }
}
