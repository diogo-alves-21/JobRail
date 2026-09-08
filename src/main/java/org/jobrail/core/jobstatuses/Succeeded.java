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
public class Succeeded implements JobStatus {

    private static final Succeeded INSTANCE = new Succeeded();

    @Override
    public String getName() {
        return "SUCCEEDED";
    }

    public static Succeeded provider() {
        return INSTANCE;
    }
}
