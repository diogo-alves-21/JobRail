/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EnqueueJobDTO(@NotBlank String type, @NotBlank String payload, @Min(1) int maxAttempts) {
}
