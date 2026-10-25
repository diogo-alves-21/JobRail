/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.exceptions;

import java.util.UUID;

public class RequeueJobException extends RuntimeException {
    public RequeueJobException(UUID id) {
        super("Unable to find job to requeue with id: " + id);
    }
}
