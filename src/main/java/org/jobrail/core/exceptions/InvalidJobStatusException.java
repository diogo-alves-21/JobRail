/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.exceptions;

public class InvalidJobStatusException extends RuntimeException {

    public InvalidJobStatusException(String status) {
        super(status == null ? "Job status can't be null" : "Unknown job status: " + status);
    }
}
