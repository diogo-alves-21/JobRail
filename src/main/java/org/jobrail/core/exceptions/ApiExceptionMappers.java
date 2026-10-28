/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ApiExceptionMappers {

    @ServerExceptionMapper
    public Response mapQueueJob(QueueJobException e) {
        return error(Response.Status.BAD_REQUEST, e);
    }

    @ServerExceptionMapper
    public Response mapInvalidJobStatus(InvalidJobStatusException e) {
        return error(Response.Status.BAD_REQUEST, e);
    }

    @ServerExceptionMapper
    public Response mapJobNotFound(JobNotFoundException e) {
        return error(Response.Status.NOT_FOUND, e);
    }

    @ServerExceptionMapper
    public Response mapRequeueJob(RequeueJobException e) {
        return error(Response.Status.NOT_FOUND, e);
    }

    private Response error(Response.Status status, RuntimeException e) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorResponse(e.getMessage()))
                .build();
    }
}
