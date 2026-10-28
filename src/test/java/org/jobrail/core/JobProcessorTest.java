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
import org.mockito.stubbing.OngoingStubbing;

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

    // ---- claimAndRunOnce: outcome of a single claimed job -------------------------------------------

    @Test
    void handlerSucceeds_claimAndRunOnce_jobIsMarkedAsSucceededOneTime() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 1));

        assertTrue(processor.claimAndRunOnce());

        verify(handler, times(1)).handleJob(withAttempts(job, 1));
        verify(repository, times(1)).markSucceeded(job.id());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void alwaysFailingHandler_claimAndRunOnce_deadAfterExactlyMaxAttempts() {
        int maxAttempts = 3;
        Job job = pendingJob(maxAttempts);
        stubFlow(job, withAttempts(job, 1), withAttempts(job, 2), withAttempts(job, 3));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        for (int pass = 0; pass < maxAttempts; pass++) {
            processor.claimAndRunOnce();
        }

        verify(handler, times(maxAttempts)).handleJob(any());
        verify(repository, times(maxAttempts - 1)).reschedule(eq(job.id()), any(), any());
        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).markSucceeded(any());
    }

    @Test
    void handlerFailsOnce_claimAndRunOnce_jobIsMarkedAsSucceededAfterOneFailure() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 1), withAttempts(job, 2));
        doThrow(new HandleJobException("boom")).doNothing().when(handler).handleJob(any());

        processor.claimAndRunOnce();
        processor.claimAndRunOnce();

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

        processor.claimAndRunOnce();

        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markSucceeded(any());
    }

    @Test
    void failureBeforeLastAttempt_claimAndRunOnce_reschedulesInsteadOfDead() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 2));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.claimAndRunOnce();

        verify(repository, times(1)).reschedule(eq(job.id()), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void failureOnLastAttempt_claimAndRunOnce_marksDeadInsteadOfReschedule() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 3));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.claimAndRunOnce();

        verify(repository, times(1)).markDead(eq(job.id()), any());
        verify(repository, never()).reschedule(any(), any(), any());
    }

    @Test
    void failures_claimAndRunOnce_rescheduleWithGrowingBackoff() {
        Job job = pendingJob(5);
        stubFlow(job, withAttempts(job, 1), withAttempts(job, 2));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        Instant beforeFirst = Instant.now();
        processor.claimAndRunOnce();
        Instant afterFirst = Instant.now();
        Instant beforeSecond = Instant.now();
        processor.claimAndRunOnce();
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
    void failure_claimAndRunOnce_reschedulesWithUpdatedAtFromUpdateAttempts() {
        Job job = pendingJob(3);
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.claimAndRunOnce();

        verify(repository, times(1)).reschedule(eq(job.id()), eq(fresh.updatedAt()), any());
    }

    @Test
    void exhaustedAttempts_claimAndRunOnce_marksDeadWithUpdatedAtFromUpdateAttempts() {
        Job job = pendingJob(1);
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        processor.claimAndRunOnce();

        verify(repository, times(1)).markDead(eq(job.id()), eq(fresh.updatedAt()));
    }

    @Test
    void failedJob_claimAndRunOnce_stillReportsThatWorkWasDone() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 1));
        doThrow(new HandleJobException("boom")).when(handler).handleJob(any());

        assertTrue(processor.claimAndRunOnce());
    }

    // ---- claimAndRunOnce: nothing to claim ----------------------------------------------------------

    @Test
    void nothingToClaim_claimAndRunOnce_returnsFalseAndTouchesNothing() {
        when(repository.claimNext()).thenReturn(List.of());

        assertFalse(processor.claimAndRunOnce());

        verify(repository, times(1)).claimNext();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(handler);
    }

    @Test
    void anyOutcome_claimAndRunOnce_doesNotScanForStuckJobs() {
        Job job = pendingJob(3);
        stubFlow(job, withAttempts(job, 1));

        processor.claimAndRunOnce();

        verify(repository, never()).listJobsByStatus(any());
    }

    // ---- claimAndRunOnce: one job per call, so a bad job must not affect the next call --------------

    @Test
    void oneJobFails_claimAndRunOnce_nextCallStillHandlesTheNextJob() {
        Job failing = pendingJob(3);
        Job next = pendingJob(3);
        Job failingFresh = withAttempts(failing, 1);
        Job nextFresh = withAttempts(next, 1);
        stubClaimSequence(failing, next);
        when(repository.updateAttempts(failing.id())).thenReturn(failingFresh);
        when(repository.updateAttempts(next.id())).thenReturn(nextFresh);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        doThrow(new HandleJobException("boom")).when(handler).handleJob(failingFresh);

        processor.claimAndRunOnce();
        processor.claimAndRunOnce();

        verify(repository, times(1)).reschedule(eq(failing.id()), eq(failingFresh.updatedAt()), any());
        verify(repository, never()).markSucceeded(failing.id());
        verify(handler, times(1)).handleJob(nextFresh);
        verify(repository, times(1)).markSucceeded(next.id());
        verify(repository, never()).reschedule(eq(next.id()), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void jobsOfDifferentTypes_claimAndRunOnce_eachGoesToItsOwnHandler() {
        JobHandler paymentHandler = mock(JobHandler.class);
        Job email = pendingJobOfType("email");
        Job payment = pendingJobOfType("Payment");
        Job emailFresh = withAttempts(email, 1);
        Job paymentFresh = withAttempts(payment, 1);
        stubClaimSequence(email, payment);
        when(repository.updateAttempts(email.id())).thenReturn(emailFresh);
        when(repository.updateAttempts(payment.id())).thenReturn(paymentFresh);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
        when(registry.getHandler("PAYMENT")).thenReturn(paymentHandler);

        processor.claimAndRunOnce();
        processor.claimAndRunOnce();

        verify(handler, times(1)).handleJob(emailFresh);
        verify(handler, never()).handleJob(paymentFresh);
        verify(paymentHandler, times(1)).handleJob(paymentFresh);
        verify(paymentHandler, never()).handleJob(emailFresh);
        verify(repository, times(1)).markSucceeded(email.id());
        verify(repository, times(1)).markSucceeded(payment.id());
    }

    // ---- claimAndRunOnce: missing handler -----------------------------------------------------------

    @Test
    void noHandlerRegisteredForType_claimAndRunOnce_jobIsMarkedDeadImmediately() {
        Job job = pendingJobOfType("unknown");
        Job fresh = withAttempts(job, 1, Instant.now().truncatedTo(ChronoUnit.MICROS));
        stubFlow(job, fresh);
        when(registry.getHandler("UNKNOWN")).thenReturn(null);

        assertTrue(processor.claimAndRunOnce());

        verify(registry, times(1)).getHandler("UNKNOWN");
        verify(repository, times(1)).markDead(job.id(), fresh.updatedAt());
        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markSucceeded(any());
        verifyNoInteractions(handler);
    }

    @Test
    void noHandlerForOneJob_claimAndRunOnce_nextCallStillHandlesTheNextJob() {
        Job unknown = pendingJobOfType("unknown");
        Job email = pendingJobOfType("email");
        Job unknownFresh = withAttempts(unknown, 1);
        Job emailFresh = withAttempts(email, 1);
        stubClaimSequence(unknown, email);
        when(repository.updateAttempts(unknown.id())).thenReturn(unknownFresh);
        when(repository.updateAttempts(email.id())).thenReturn(emailFresh);
        when(registry.getHandler("UNKNOWN")).thenReturn(null);
        when(registry.getHandler("EMAIL")).thenReturn(handler);

        processor.claimAndRunOnce();
        processor.claimAndRunOnce();

        verify(repository, times(1)).markDead(eq(unknown.id()), any());
        verify(handler, times(1)).handleJob(emailFresh);
        verify(repository, times(1)).markSucceeded(email.id());
        verify(repository, never()).markDead(eq(email.id()), any());
    }

    // ---- reclaimStuckJobs ---------------------------------------------------------------------------

    @Test
    void processingJobWithinTimeout_reclaimStuckJobs_isLeftAlone() {
        Job healthy = processingJob(1, 3, Instant.now().minusSeconds(10));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(healthy));

        processor.reclaimStuckJobs();

        verify(repository, never()).reschedule(any(), any(), any());
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void stuckJobWithAttemptsLeft_reclaimStuckJobs_isRescheduledWithBackoffAndGuard() {
        Job stuck = processingJob(1, 3, Instant.now().minusSeconds(120));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));

        Instant before = Instant.now();
        processor.reclaimStuckJobs();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> runAfter = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(1)).reschedule(eq(stuck.id()), eq(stuck.updatedAt()), runAfter.capture());
        // the crashed attempt is already counted in currentAttempts, so the next one is currentAttempts + 1
        assertFalse(runAfter.getValue().isBefore(before.plus(Backoff.runAfter(2))));
        assertFalse(runAfter.getValue().isAfter(after.plus(Backoff.runAfter(2))));
        verify(repository, never()).markDead(any(), any());
    }

    @Test
    void stuckJobWithAttemptsExhausted_reclaimStuckJobs_isMarkedDeadWithGuard() {
        Job stuck = processingJob(3, 3, Instant.now().minusSeconds(120));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(stuck));

        processor.reclaimStuckJobs();

        verify(repository, times(1)).markDead(stuck.id(), stuck.updatedAt());
        verify(repository, never()).reschedule(any(), any(), any());
    }

    @Test
    void severalStuckJobs_reclaimStuckJobs_eachGetsItsOwnOutcome() {
        Job retryable = processingJob(1, 3, Instant.now().minusSeconds(120));
        Job exhausted = processingJob(3, 3, Instant.now().minusSeconds(120));
        Job healthy = processingJob(1, 3, Instant.now().minusSeconds(5));
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of(retryable, exhausted, healthy));

        processor.reclaimStuckJobs();

        verify(repository, times(1)).reschedule(eq(retryable.id()), eq(retryable.updatedAt()), any());
        verify(repository, times(1)).markDead(exhausted.id(), exhausted.updatedAt());
        verify(repository, never()).reschedule(eq(healthy.id()), any(), any());
        verify(repository, never()).markDead(eq(healthy.id()), any());
    }

    @Test
    void anyOutcome_reclaimStuckJobs_neverClaimsJobs() {
        when(repository.listJobsByStatus(Processing.getInstance())).thenReturn(List.of());

        processor.reclaimStuckJobs();

        verify(repository, never()).claimNext();
    }

    // ---- helpers ------------------------------------------------------------------------------------

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

    private Job pendingJob(int maxAttempts) {
        Instant past = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        return new Job(UUID.randomUUID(), "email", "{}", Pending.getInstance(), 0, maxAttempts, past, past, past);
    }

    /** The same job is claimed on every call; each call gets the next fresh job from updateAttempts. */
    private void stubFlow(Job job, Job first, Job... rest) {
        when(repository.claimNext()).thenReturn(List.of(job));
        when(repository.updateAttempts(job.id())).thenReturn(first, rest);
        when(registry.getHandler("EMAIL")).thenReturn(handler);
    }

    /** Call 1 claims the first job, call 2 the second, and so on. */
    private void stubClaimSequence(Job first, Job... rest) {
        OngoingStubbing<List<Job>> claims = when(repository.claimNext()).thenReturn(List.of(first));
        for (Job next : rest) {
            claims = claims.thenReturn(List.of(next));
        }
    }

    private static Job withAttempts(Job j, int attempts) {
        return withAttempts(j, attempts, j.updatedAt());
    }

    private static Job withAttempts(Job j, int attempts, Instant updatedAt) {
        return new Job(j.id(), j.type(), j.payload(), j.status(), attempts, j.maxAttempts(), j.runAfter(),
                j.createdAt(), updatedAt);
    }
}
