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
public class Processing implements JobStatus {

    private static final Processing INSTANCE = new Processing();

    @Override
    public String getName() {
        return "PROCESSING";
    }

    public static Processing provider(){
        return INSTANCE;
    }
}
