package org.jobrail.core;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JobStatuses {

    private static final Map<String, JobStatus> BY_NAME =
            ServiceLoader.load(JobStatus.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .collect(Collectors.toUnmodifiableMap(JobStatus::getName, Function.identity()));

    public static JobStatus fromName(String name) {
        JobStatus status = BY_NAME.get(name);

        if (status == null) throw new IllegalArgumentException("Unknown job status: " + name);
        return status;
    }
}
