/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jobrail.api.dto.GroupedStat;
import org.jobrail.api.dto.JobsStatsResponseDTO;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Pending;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag("unit")
class JobsStatsResponseDTOTest {

    @Test
    void countsByStatus_from_mapsEachEntryAndKeepsTheOrder() {
        Map<JobStatus, Long> counts = new LinkedHashMap<>();
        counts.put(Pending.getInstance(), 3L);
        counts.put(Dead.getInstance(), 0L);

        JobsStatsResponseDTO dto = JobsStatsResponseDTO.from(counts);

        assertEquals(List.of(new GroupedStat(3L, "PENDING"), new GroupedStat(0L, "DEAD")), dto.groupedStats());
    }

    @Test
    void noCounts_from_returnsAnEmptyList() {
        assertEquals(List.of(), JobsStatsResponseDTO.from(Map.of()).groupedStats());
    }
}
