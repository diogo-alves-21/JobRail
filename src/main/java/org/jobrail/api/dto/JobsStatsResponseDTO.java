/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api.dto;

import org.jobrail.core.JobStatus;

import java.util.List;
import java.util.Map;

public record JobsStatsResponseDTO(List<GroupedStat> groupedStats) {

    public static JobsStatsResponseDTO from(Map<JobStatus, Long> countByStatus) {
        return new JobsStatsResponseDTO(countByStatus.entrySet().stream()
                .map(entry -> new GroupedStat(entry.getValue(), entry.getKey().getName())).toList());
    }
}
