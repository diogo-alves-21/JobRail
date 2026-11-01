/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.jobrail.core.jobstatuses.Succeeded;
import org.jobrail.core.queries.GetJobsStats;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Tag("unit")
class GetJobsStatsTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final GetJobsStats getJobsStats = new GetJobsStats(repository);

    @Test
    void noJobs_query_reportsEveryStatusAsZero() {
        when(repository.countJobsByStatus()).thenReturn(Map.of());

        Map<JobStatus, Long> stats = getJobsStats.query();

        assertEquals(4, stats.size());
        stats.values().forEach(count -> assertEquals(0L, count));
    }

    @Test
    void someStatusesMissing_query_keepsTheCountsAndZeroFillsTheRest() {
        when(repository.countJobsByStatus()).thenReturn(Map.of(Pending.getInstance(), 5L, Dead.getInstance(), 2L));

        Map<JobStatus, Long> stats = getJobsStats.query();

        assertEquals(5L, stats.get(Pending.getInstance()));
        assertEquals(2L, stats.get(Dead.getInstance()));
        assertEquals(0L, stats.get(Processing.getInstance()));
        assertEquals(0L, stats.get(Succeeded.getInstance()));
    }

    @Test
    void anyCounts_query_followsTheLifecycleOrderOfTheRegistry() {
        when(repository.countJobsByStatus()).thenReturn(Map.of(Dead.getInstance(), 1L, Pending.getInstance(), 1L));

        List<JobStatus> order = List.copyOf(getJobsStats.query().keySet());

        assertEquals(JobStatusRegistry.all(), order);
    }
}
