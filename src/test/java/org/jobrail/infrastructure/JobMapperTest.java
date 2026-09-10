/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.jobrail.core.Job;
import org.jobrail.core.jobstatuses.Pending;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@Tag(value = "unit")
public class JobMapperTest {

    private final JobMapper jobMapper = Mappers.getMapper(JobMapper.class);

    @Test
    void entity_toDomain_returnsRespectiveDomain() {

        JobEntity entity = new JobEntity();
        entity.setId(UUID.randomUUID());
        entity.setType("email");
        entity.setPayload("{}");
        entity.setStatus(Pending.provider());
        entity.setCurrentAttempts(0);
        entity.setMaxAttempts(10);

        Job job = jobMapper.toDomain(entity);

        assertEquals(job.id(), entity.getId());
        assertEquals(job.type(), entity.getType());
        assertEquals(job.payload(), entity.getPayload());
        assertEquals(job.status(), entity.getStatus());
        assertEquals(job.currentAttempts(), entity.getCurrentAttempts());
        assertEquals(job.maxAttempts(), entity.getMaxAttempts());
    }

    @Test
    void domain_toEntity_returnsRespectiveEntity() {

        Job job =
                new Job(
                        UUID.randomUUID(),
                        "email",
                        "{}",
                        Pending.provider(),
                        0,
                        10,
                        Instant.now(),
                        Instant.now(),
                        Instant.now());

        JobEntity entity = jobMapper.toEntity(job);

        assertEquals(entity.getId(), job.id());
        assertEquals(entity.getType(), job.type());
        assertEquals(entity.getPayload(), job.payload());
        assertEquals(entity.getStatus(), job.status());
        assertEquals(entity.getCurrentAttempts(), job.currentAttempts());
        assertEquals(entity.getMaxAttempts(), job.maxAttempts());
        assertEquals(entity.getRunAfter(), job.runAfter());
    }
}
