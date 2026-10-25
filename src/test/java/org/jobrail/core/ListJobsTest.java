/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.jobrail.core.exceptions.InvalidJobStatusException;
import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.queries.ListJobs;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@Tag("unit")
class ListJobsTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final ListJobs listJobs = new ListJobs(repository);

    @Test
    void validStatus_query_filtersByThatStatus() {
        List<Job> deadJobs = List.of();
        when(repository.listJobsByStatus(Dead.getInstance())).thenReturn(deadJobs);

        List<Job> result = listJobs.query("dead");

        assertSame(deadJobs, result);
        verify(repository, times(1)).listJobsByStatus(Dead.getInstance());
    }

    @Test
    void nullStatus_query_listsWithoutFilter() {
        listJobs.query(null);

        verify(repository, times(1)).listJobsByStatus(null);
    }

    @Test
    void blankStatus_query_listsWithoutFilter() {
        listJobs.query("   ");

        verify(repository, times(1)).listJobsByStatus(null);
    }

    @Test
    void unknownStatus_query_throwsInvalidJobStatusAndNeverQueriesTheRepository() {
        InvalidJobStatusException exception = assertThrows(InvalidJobStatusException.class,
                () -> listJobs.query("bogus"));

        assertEquals("Unknown job status: BOGUS", exception.getMessage());
        verifyNoInteractions(repository);
    }
}
