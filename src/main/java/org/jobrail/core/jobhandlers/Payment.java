package org.jobrail.core.jobhandlers;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jobrail.core.Job;
import org.jobrail.core.JobHandler;

import java.util.Random;
import java.util.logging.Level;

@ApplicationScoped
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Log
public class Payment implements JobHandler {

    private static final Payment INSTANCE = new Payment();

    @Override
    public String supportedType() {
        return "PAYMENT";
    }

    @Override
    public int handleJob(Job job) {
        int currentAttempt = 1;
        Random random = new Random();
        while (currentAttempt <= job.maxAttempts()) {
            if (random.nextInt(10) < 5){
                log.log(Level.INFO, "Payment executed with success!");
                break;
            }
            currentAttempt++;
        }
        return currentAttempt;
    }

    public Payment getInstance(){
        return INSTANCE;
    }
}
