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
public class Dead implements JobStatus {

    private static final Dead INSTANCE = new Dead();

    @Override
    public String getName() {
        return "DEAD";
    }

    public static Dead provider() {
        return INSTANCE;
    }
}
