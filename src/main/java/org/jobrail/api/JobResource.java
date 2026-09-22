/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jobrail.api.dto.QueueJobDTO;
import org.jobrail.core.JobQueue;

import java.util.UUID;

@Path("/api/jobs")
@RequiredArgsConstructor
public class JobResource {

    private final JobQueue jobQueue;

    @POST
    @Path("")
    public Response queueJob(QueueJobDTO jobDTO) {
        UUID jobId = jobQueue.queue(jobDTO.type(), jobDTO.payload(), jobDTO.maxAttempts());
        return Response.ok(jobId).build();
    }
}
