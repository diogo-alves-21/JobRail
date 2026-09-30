/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.jobhandlers;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.jobrail.core.Job;
import org.jobrail.core.JobHandler;

import java.util.Random;
import java.util.logging.Level;

@ApplicationScoped
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log
public class Email implements JobHandler {

    private static final Email INSTANCE = new Email();

    @Override
    public String supportedType() {
        return "EMAIL";
    }

    @Override
    public int handleJob(Job job) {
        int currentAttempt = 1;
        Random random = new Random();
        while (currentAttempt <= job.maxAttempts()) {
            if (random.nextInt(10) < 5){
                log.log(Level.INFO, "Email sent with success!");
                break;
            }
            currentAttempt++;
        }
        return currentAttempt;
    }

    public Email getInstance() {
        return INSTANCE;
    }
}
