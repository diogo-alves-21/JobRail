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
public class Pending implements JobStatus {

    private static final Pending INSTANCE = new Pending();

    @Override
    public String getName() {
        return "PENDING";
    }

    public static Pending provider() {
        return INSTANCE;
    }
}
