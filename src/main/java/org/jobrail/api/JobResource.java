/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jobrail.api.dto.JobsStatsResponseDTO;
import org.jobrail.api.dto.QueueJobDTO;
import org.jobrail.core.*;
import org.jobrail.core.queries.GetJob;
import org.jobrail.core.queries.GetJobsStats;
import org.jobrail.core.queries.ListJobs;

import java.util.List;
import java.util.UUID;

@Path("/api/jobs")
@RequiredArgsConstructor
public class JobResource {

    private final JobQueue jobQueue;
    private final GetJob getJob;
    private final ListJobs listJobs;
    private final JobRequeue jobRequeue;
    private final GetJobsStats getJobsStats;

    @POST
    @Path("")
    public Response queueJob(QueueJobDTO jobDTO) {
        UUID jobId = jobQueue.queue(jobDTO.type(), jobDTO.payload(), jobDTO.maxAttempts());
        return Response.ok(jobId).build();
    }

    @GET
    @Path("/{id}")
    public Response getJob(@PathParam("id") UUID id) {
        Job job = getJob.query(id);
        return Response.ok(job).build();
    }

    @GET
    @Path("")
    public Response listJobs(@QueryParam("status") String status) {
        List<Job> jobs = listJobs.query(status);
        return Response.ok(jobs).build();
    }

    @POST
    @Path("/{id}/retry")
    public Response requeueJob(@PathParam("id") UUID id) {
        UUID jobId = jobRequeue.requeue(id);
        return Response.ok(jobId).build();
    }

    @GET
    @Path("/stats")
    public Response getJobsStats() {
        return Response.ok(JobsStatsResponseDTO.from(getJobsStats.query())).build();
    }
}
