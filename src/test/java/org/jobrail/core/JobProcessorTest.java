/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.jobrail.core.exceptions.HandleJobException;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Processing;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Tag("unit")
class JobProcessorTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
    private final JobHandler handler = mock(JobHandler.class);
    private final JobProcessor processor = new JobProcessor(repository, registry);

    @Test
    void handlerSucceeds_process_jobIsMarkedAsSucceededOneTime() {
        int maxAttempts = 3;
        Instant past = Instant.now().minusSeconds(60);
        Job job = new Job(UUID.randomUUID(), "EMAIL", "{}", Pending.getInstance(), 0, maxAttempts, past, past, past);

        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(job));
        when(repository.claim(any())).thenReturn(List.of(job));
        when(repository.updateAttempts(job.id())).thenReturn(withAttempts(job, 1));
        when(registry.getHandler("EMAIL")).thenReturn(handler);

        processor.process();

        verify(handler, times(1)).handleJob(withAttempts(job,1));
        verify(repository, times(1)).markSucceeded(job.id());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void alwaysFailingHandler_process_deadAfterExactlyMaxAttempts() {
        int maxAttempts = 3;
        Instant past = Instant.now().minusSeconds(60);
        Job job = new Job(UUID.randomUUID(), "email", "{}", Pending.getInstance(), 0, maxAttempts, past, past, past);

        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(job));
        when(repository.claim(any())).thenReturn(List.of(job));
        when(repository.updateAttempts(job.id())).thenReturn(withAttempts(job, 1), withAttempts(job, 2),
                withAttempts(job, 3));
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        for (int pass = 0; pass < maxAttempts; pass++) {
            processor.process();
        }

        verify(handler, times(maxAttempts)).handleJob(any());
        verify(repository, times(maxAttempts - 1)).reschedule(eq(job.id()), any(), any());
        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).markSucceeded(any());
    }

    @Test
    void handlerFailsOnce_process_jobIsMarkedAsSucceededAfterOneFailure() {
        int maxAttempts = 3;
        Instant past = Instant.now().minusSeconds(60);
        Job job = new Job(UUID.randomUUID(), "email", "{}", Pending.getInstance(), 0, maxAttempts, past, past, past);

        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(job));
        when(repository.claim(any())).thenReturn(List.of(job));
        when(repository.updateAttempts(job.id())).thenReturn(withAttempts(job, 1), withAttempts(job, 2));
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        doThrow(new HandleJobException("boom")).doNothing().when(handler).handleJob(any());

        processor.process();
        processor.process();

        verify(handler, times(2)).handleJob(any());
        verify(repository, times(1)).reschedule(eq(job.id()), any(), any());
        verify(repository, times(1)).markSucceeded(job.id());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void maxAttemptsOne_firstFailure_marksDeadWithoutReschedule() {
        Job job = pendingJob(1);
        stubFlow(job, withAttempts(job, 1));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.process();

        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markSucceeded(any());
    }

    @Test
    void failureBeforeLastAttempt_process_reschedulesInsteadOfDead() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 2));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.process();

        verify(repository, times(1)).reschedule(eq(job.id()), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void failureOnLastAttempt_process_marksDeadInsteadOfReschedule() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 3));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.process();

        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).reschedule(any(), any(), any());
    }

    @Test
    void failures_process_rescheduleWithGrowingBackoff() {
        Job job = pendingJob(5);
        stubFlow(job, withAttempts(job, 1), withAttempts(job, 2));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        Instant beforeFirst = Instant.now();
        processor.process();
        Instant afterFirst = Instant.now();
        Instant beforeSecond = Instant.now();
        processor.process();
        Instant afterSecond = Instant.now();

        ArgumentCaptor<Instant> runAfter = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(2)).reschedule(eq(job.id()), any(), runAfter.capture());
        Instant first = runAfter.getAllValues().get(0);
        Instant second = runAfter.getAllValues().get(1);

        assertFalse(first.isBefore(beforeFirst.plus(Backoff.runAfter(1))));
        assertFalse(first.isAfter(afterFirst.plus(Backoff.runAfter(1))));
        assertFalse(second.isBefore(beforeSecond.plus(Backoff.runAfter(2))));
        assertFalse(second.isAfter(afterSecond.plus(Backoff.runAfter(2))));
        assertTrue(Backoff.runAfter(2).compareTo(Backoff.runAfter(1)) > 0);
    }

    @Test
    void failure_process_reschedulesWithUpdatedAtFromUpdateAttempts() {
        Job job = pendingJob(3);
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.process();

        verify(repository, times(1)).reschedule(eq(job.id()), eq(fresh.updatedAt()), any());
    }

    @Test
    void exhaustedAttempts_process_marksDeadWithUpdatedAtFromUpdateAttempts() {
        Job job = pendingJob(1);
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.process();

        verify(repository, times(1)).markDead(eq(job.id()), eq(fresh.updatedAt()));
    }

    @Test
    void noPendingJobs_process_nothingIsClaimedOrHandled() {
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of());

        processor.process();

        verify(repository, never()).claim(any());
        verify(repository, never()).updateAttempts(any());
        verifyNoInteractions(handler);
    }

    @Test
    void notYetDueJob_process_isNotClaimed() {
        Job notDue = pendingJobDueAt(Instant.now().plusSeconds(60));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(notDue));

        processor.process();

        verify(repository, never()).claim(any());
        verify(repository, never()).updateAttempts(any());
        verifyNoInteractions(handler);
    }

    @Test
    void dueAndNotDueJobs_process_onlyTheDueJobIsClaimedAndHandled() {
        Job due = pendingJobDueAt(Instant.now().minusSeconds(60));
        Job notDue = pendingJobDueAt(Instant.now().plusSeconds(60));
        Job dueFresh = withAttempts(due, 1);
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(due, notDue));
        when(repository.claim(List.of(due))).thenReturn(List.of(due));
        when(repository.updateAttempts(due.id())).thenReturn(dueFresh);
        when(registry.getHandler("EMAIL")).thenReturn(handler);

        processor.process();

        verify(repository, times(1)).claim(List.of(due));
        verify(repository, never()).updateAttempts(notDue.id());
        verify(handler, times(1)).handleJob(dueFresh);
        verify(repository, times(1)).markSucceeded(due.id());
        verify(repository, never()).markSucceeded(notDue.id());
    }

    @Test
    void claimReturnsEmpty_process_nothingIsHandled() {
        Job due = pendingJobDueAt(Instant.now().minusSeconds(60));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(due));
        when(repository.claim(any())).thenReturn(List.of());

        processor.process();

        verify(repository, times(1)).claim(List.of(due));
        verify(repository, never()).updateAttempts(any());
        verify(repository, never()).markSucceeded(any());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markDead(any(), any());
        verifyNoInteractions(handler);
    }

    @Test
    void oneJobFailsInBatch_process_otherJobsAreStillHandled() {
        Job first = pendingJob(3);
        Job failing = pendingJob(3);
        Job last = pendingJob(3);
        Job firstFresh = withAttempts(first, 1);
        Job failingFresh = withAttempts(failing, 1);
        Job lastFresh = withAttempts(last, 1);
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(first, failing, last));
        when(repository.claim(any())).thenReturn(List.of(first, failing, last));
        when(repository.updateAttempts(first.id())).thenReturn(firstFresh);
        when(repository.updateAttempts(failing.id())).thenReturn(failingFresh);
        when(repository.updateAttempts(last.id())).thenReturn(lastFresh);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(failingFresh);

        processor.process();

        verify(handler, times(3)).handleJob(any());
        verify(repository, times(1)).markSucceeded(first.id());
        verify(repository, times(1)).markSucceeded(last.id());
        verify(repository, never()).markSucceeded(failing.id());
        verify(repository, times(1)).reschedule(eq(failing.id()), eq(failingFresh.updatedAt()), any());
        verify(repository, never()).reschedule(eq(first.id()), any(), any());
        verify(repository, never()).reschedule(eq(last.id()), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void jobsOfDifferentTypes_process_eachGoesToItsOwnHandler() {
        JobHandler paymentHandler = mock(JobHandler.class);
        Job email = pendingJobOfType("email");
        Job payment = pendingJobOfType("Payment");
        Job emailFresh = withAttempts(email, 1);
        Job paymentFresh = withAttempts(payment, 1);
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(email, payment));
        when(repository.claim(any())).thenReturn(List.of(email, payment));
        when(repository.updateAttempts(email.id())).thenReturn(emailFresh);
        when(repository.updateAttempts(payment.id())).thenReturn(paymentFresh);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        when(registry.getHandler("PAYMENT")).thenReturn(paymentHandler);

        processor.process();

        verify(handler, times(1)).handleJob(emailFresh);
        verify(handler, never()).handleJob(paymentFresh);
        verify(paymentHandler, times(1)).handleJob(paymentFresh);
        verify(paymentHandler, never()).handleJob(emailFresh);
        verify(repository, times(1)).markSucceeded(email.id());
        verify(repository, times(1)).markSucceeded(payment.id());
    }

    @Test
    void processingJobWithinTimeout_process_isLeftAlone() {
        Job healthy = processingJob(1, 3, Instant.now().minusSeconds(10));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(healthy));
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of());

        processor.process();

        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void stuckJobWithAttemptsLeft_process_isRescheduledWithBackoffAndGuard() {
        Job stuck = processingJob(1, 3, Instant.now().minusSeconds(120));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of());

        Instant before = Instant.now();
        processor.process();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> runAfter = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(1)).reschedule(eq(stuck.id()), eq(stuck.updatedAt()), runAfter.capture());
        assertFalse(runAfter.getValue().isBefore(before.plus(Backoff.runAfter(2))));
        assertFalse(runAfter.getValue().isAfter(after.plus(Backoff.runAfter(2))));
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void stuckJobWithAttemptsExhausted_process_isMarkedDeadWithGuard() {
        Job stuck = processingJob(3, 3, Instant.now().minusSeconds(120));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of());

        processor.process();

        verify(repository, times(1)).markDead(stuck.id(), stuck.updatedAt());
        verify(repository, never()).reschedule(any(), any(), any());
    }

    @Test
    void stuckJobAndEmptyPendingQueue_process_stillReclaimsTheStuckJob() {
        Job stuck = processingJob(1, 3, Instant.now().minusSeconds(120));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of());

        processor.process();

        verify(repository, times(1)).reschedule(eq(stuck.id()), any(), any());
        verify(repository, never()).claim(any());
    }

    @Test
    void stuckJobAndDuePendingJob_process_reclaimsBeforeClaiming() {
        Job stuck = processingJob(1, 3, Instant.now().minusSeconds(120));
        Job due = pendingJobDueAt(Instant.now().minusSeconds(60));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(due));
        when(repository.claim(any())).thenReturn(List.of());

        processor.process();

        InOrder order = inOrder(repository);
        order.verify(repository).reschedule(eq(stuck.id()), any(), any());
        order.verify(repository).claim(List.of(due));
    }

    @Test
    void noHandlerRegisteredForType_process_jobIsMarkedDeadImmediately() {
        Job job = pendingJobOfType("unknown");
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        when(registry.getHandler("UNKNOWN")).thenReturn(null);

        processor.process();

        verify(registry, times(1)).getHandler("UNKNOWN");
        verify(repository, times(1)).markDead(job.id(), fresh.updatedAt());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markSucceeded(any());
        verifyNoInteractions(handler);
    }

    @Test
    void noHandlerForOneJobInBatch_process_otherJobsAreStillHandled() {
        Job unknown = pendingJobOfType("unknown");
        Job email = pendingJobOfType("email");
        Job unknownFresh = withAttempts(unknown, 1);
        Job emailFresh = withAttempts(email, 1);
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(unknown, email));
        when(repository.claim(any())).thenReturn(List.of(unknown, email));
        when(repository.updateAttempts(unknown.id())).thenReturn(unknownFresh);
        when(repository.updateAttempts(email.id())).thenReturn(emailFresh);
        when(registry.getHandler("UNKNOWN")).thenReturn(null);
        when(registry.getHandler("EMAIL")).thenReturn(handler);

        processor.process();

        verify(repository, times(1)).markDead(eq(unknown.id()), any());
        verify(handler, times(1)).handleJob(emailFresh);
        verify(repository, times(1)).markSucceeded(email.id());
        verify(repository, never()).markDead(eq(email.id()), any());
    }

    private Job processingJob(int currentAttempts, int maxAttempts, Instant updatedAt) {
        Instant created = Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MICROS);
        return new Job(UUID.randomUUID(), "email", "{}", Processing.getInstance(), currentAttempts, maxAttempts,
                created, created, updatedAt.truncatedTo(ChronoUnit.MICROS));
    }

    private Job pendingJobOfType(String type) {
        Job base = pendingJob(3);
        return new Job(base.id(), type, base.payload(), base.status(), base.currentAttempts(), base.maxAttempts(),
                base.runAfter(), base.createdAt(), base.updatedAt());
    }

    private Job pendingJobDueAt(Instant runAfter) {
        Instant created = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS);
        return new Job(UUID.randomUUID(), "email", "{}", Pending.getInstance(), 0, 3, runAfter, created, created);
    }

    private Job pendingJob(int maxAttempts) {
        Instant past = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        return new Job(UUID.randomUUID(), "email", "{}", Pending.getInstance(), 0, maxAttempts, past, past, past);
    }

    private void stubFlow(Job job, Job first, Job... rest) {
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());
        when(repository.listJobsByStatus(Pending.getInstance())).thenReturn(List.of(job));
        when(repository.claim(any())).thenReturn(List.of(job));
        when(repository.updateAttempts(job.id())).thenReturn(first, rest);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
    }

    private static Job withAttempts(Job j, int attempts) {
        return withAttempts(j, attempts, j.updatedAt());
    }

    private static Job withAttempts(Job j, int attempts, Instant updatedAt) {
        return new Job(j.id(), j.type(), j.payload(), j.status(), attempts, j.maxAttempts(), j.runAfter(),
                j.createdAt(), updatedAt);
    }
}
