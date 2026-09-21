package org.jobrail.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jobrail.api.dto.EnqueueJobDTO;
import org.jobrail.core.Job;
import org.jobrail.core.JobQueue;

import java.util.UUID;

@Path("/api/jobs")
@RequiredArgsConstructor
public class JobResource {

    private final JobQueue jobQueue;

    @POST
    @Path("")
    public Response enqueueJob(EnqueueJobDTO jobDTO) {
        UUID jobId = jobQueue.enqueue(jobDTO.type(), jobDTO.payload(), jobDTO.maxAttempts());
        return Response.ok(jobId).build();
    }
}
