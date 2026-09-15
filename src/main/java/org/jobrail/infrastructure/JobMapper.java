/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import org.jobrail.core.Job;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface JobMapper {

    Job toDomain(JobEntity entity);

    JobEntity toEntity(Job job);
}
