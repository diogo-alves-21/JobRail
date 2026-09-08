/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.jobstatuses;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jobrail.core.JobStatus;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Failed implements JobStatus {

    private static final Failed INSTANCE = new Failed();

    @Override
    public String getName() {
        return "FAILED";
    }

    public static Failed provider() {
        return INSTANCE;
    }
}
