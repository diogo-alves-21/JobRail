/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jobrail.core.exceptions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@Tag("unit")
class ApiExceptionMappersTest {

    private final ApiExceptionMappers mappers = new ApiExceptionMappers();

    @Test
    void queueJobException_isMappedTo400WithItsMessage() {
        assertError(400, "Job type can't be empty",
                mappers.mapQueueJob(new QueueJobException("Job type can't be empty")));
    }

    @Test
    void invalidJobStatusException_isMappedTo400WithItsMessage() {
        assertError(400, "Unknown job status: bogus",
                mappers.mapInvalidJobStatus(new InvalidJobStatusException("bogus")));
    }

    @Test
    void jobNotFoundException_isMappedTo404WithItsMessage() {
        JobNotFoundException exception = new JobNotFoundException();

        assertError(404, exception.getMessage(), mappers.mapJobNotFound(exception));
    }

    @Test
    void requeueJobException_isMappedTo404WithItsMessage() {
        RequeueJobException exception = new RequeueJobException(UUID.randomUUID());

        assertError(404, exception.getMessage(), mappers.mapRequeueJob(exception));
    }

    private static void assertError(int status, String message, Response response) {
        assertEquals(status, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(new ErrorResponse(message), response.getEntity());
    }
}
