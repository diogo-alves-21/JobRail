/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.exceptions;

public class QueueJobException extends RuntimeException {

    public QueueJobException(String message) {
        super(message);
    }
}
