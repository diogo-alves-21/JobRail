/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.jobhandlers;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.java.Log;
import org.jobrail.core.Job;
import org.jobrail.core.JobHandler;
import org.jobrail.core.exceptions.HandleJobException;

import java.util.Random;
import java.util.logging.Level;

@ApplicationScoped
@Log
public class Payment implements JobHandler {

    @Override
    public String supportedType() {
        return "PAYMENT";
    }

    @Override
    public void handleJob(Job job) {
        Random random = new Random();
        if (random.nextInt(10) < 5) {
            log.log(Level.INFO, "Payment executed with success!");
        } else {
            throw new HandleJobException("Error occurred while processing payment");
        }
    }
}
