/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.Job;
import org.jobrail.core.JobRepository;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public Job store(Job job) {
        JobEntity entity = jobMapper.toEntity(job);
        repositoryBase.persist(entity);
        return jobMapper.toDomain(entity);
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public List<Job> claim(List<Job> jobs) {
        List<UUID> ids = jobs.stream().map(Job::id).toList();
        List<JobEntity> entities = repositoryBase.getEntityManager()
                .createQuery("FROM JobEntity WHERE id IN :ids AND status = :status", JobEntity.class)
                .setParameter("ids", ids).setParameter("status", Pending.getInstance())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout", -2)
                .getResultList();

        entities.forEach(entity -> {
            entity.setStatus(Processing.getInstance());
            entity.setUpdatedAt(Instant.now());
        });

        return entities.stream().map(jobMapper::toDomain).toList();
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public void markSucceeded(UUID id) {
        JobEntity job = repositoryBase.findById(id);
        job.setStatus(Succeeded.getInstance());
        job.setUpdatedAt(Instant.now());
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public void reschedule(UUID id, Instant seenUpdatedAt, Instant runAfter) {
        repositoryBase.getEntityManager().createQuery("""
                UPDATE JobEntity
                SET status = :pending, runAfter = :runAfter, updatedAt = :now
                WHERE id = :id AND status = :processing AND updatedAt = :seen
                """).setParameter("pending", Pending.getInstance()).setParameter("processing", Processing.getInstance())
                .setParameter("runAfter", runAfter).setParameter("now", Instant.now()).setParameter("id", id)
                .setParameter("seen", seenUpdatedAt).executeUpdate();
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public void markDead(UUID id, Instant seenUpdatedAt) {
        repositoryBase.getEntityManager().createQuery("""
                UPDATE JobEntity
                SET status = :dead, currentAttempts = maxAttempts, updatedAt = :now
                WHERE id = :id AND status = :processing AND updatedAt = :seen
                """).setParameter("dead", Dead.getInstance()).setParameter("processing", Processing.getInstance())
                .setParameter("now", Instant.now()).setParameter("id", id).setParameter("seen", seenUpdatedAt)
                .executeUpdate();
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public Job updateAttempts(UUID id) {
        JobEntity job = repositoryBase.findById(id);
        job.setCurrentAttempts(job.getCurrentAttempts() + 1);
        job.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return jobMapper.toDomain(job);
    }
}
