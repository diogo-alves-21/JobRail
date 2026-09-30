/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlerMap;

    public JobHandlerRegistry(Instance<JobHandler> jobHandlers) {
        handlerMap = jobHandlers.stream().collect(Collectors.toMap(JobHandler::supportedType, Function.identity()));
    }

    public JobHandler getHandler(String type) {
        return handlerMap.get(type);
    }
}