/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.infrastructure;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jobrail.core.JobStatus;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job")
public class JobEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id")
    private UUID uuid;

    @Column(name = "type", nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Convert(converter = JobStatusConverter.class)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "current_attempts", nullable = false)
    private int currentAttempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
