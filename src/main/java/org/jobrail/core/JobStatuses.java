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

public final class JobStatuses {

    private static final Map<String, JobStatus> BY_NAME =
            Stream.of(
                            Pending.provider(),
                            Processing.provider(),
                            Succeeded.provider(),
                            Failed.provider(),
                            Dead.provider())
                    .collect(Collectors.toUnmodifiableMap(JobStatus::getName, Function.identity()));

    public static JobStatus fromName(String name) {
        JobStatus status = BY_NAME.get(name);

        if (status == null) throw new IllegalArgumentException("Unknown job status: " + name);
        return status;
    }
}
