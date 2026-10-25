/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import org.jobrail.core.exceptions.InvalidJobStatusException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
public class JobStatusRegistryTest {

    @Test
    void validName_fromName_returnJobStatus() {
        JobStatus jobStatus = JobStatusRegistry.fromName("PENDING");
        assertEquals("PENDING", jobStatus.getName());
    }

    @Test
    void invalidName_fromName_throwsException() {
        InvalidJobStatusException exception = assertThrows(InvalidJobStatusException.class,
                () -> JobStatusRegistry.fromName("INVALID_STATUS"));

        assertEquals("Unknown job status: INVALID_STATUS", exception.getMessage());
    }

    @Test
    void null_fromName_throwsException() {
        InvalidJobStatusException exception = assertThrows(InvalidJobStatusException.class,
                () -> JobStatusRegistry.fromName(null));

        assertEquals("Job status can't be null", exception.getMessage());
    }
}
