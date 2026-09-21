/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.Job;
import org.jobrail.core.JobRepository;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Pending;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@ApplicationScoped
public class JobPersistenceAdapter implements JobRepository {

    private final PanacheRepositoryBase<JobEntity, UUID> repositoryBase = new PanacheRepositoryBase<>() {
    };
    private final JobMapper jobMapper;

    @Override
    public Optional<Job> findByIdOptional(UUID id) {
        return repositoryBase.findByIdOptional(id).map(jobMapper::toDomain);
    }

    @Override
    public List<Job> listJobsByStatus(JobStatus status) {
        return repositoryBase.find("status = ?1", status).stream().map(jobMapper::toDomain).toList();
    }

    @Transactional
    @Override
    public Job store(Job job) {
        JobEntity entity = jobMapper.toEntity(job);
        repositoryBase.persist(entity);
        return jobMapper.toDomain(entity);
    }
}
