/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.exceptions;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException() {
        super("There is no job foe the given id");
    }
}
