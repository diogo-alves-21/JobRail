/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import java.time.Duration;

public final class Backoff {

    private static final long CAP_SECONDS = Duration.ofMinutes(5).getSeconds();
    private static final int MAX_SAFE_SHIFT = 30;

    public static Duration runAfter(int attempts) {
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts must be bigger than 0");
        }

        if (attempts > MAX_SAFE_SHIFT)
            return Duration.ofSeconds(CAP_SECONDS);

        long seconds = (long) Math.pow(2, attempts);
        return Duration.ofSeconds(seconds);
    }
}
