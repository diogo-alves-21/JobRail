/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jobrail.core.Job;
import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Dead;
import org.jobrail.core.jobstatuses.Pending;
import org.jobrail.core.jobstatuses.Succeeded;
import org.jobrail.infrastructure.JobPersistenceAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Tag("integration")
@QuarkusTest
class JobResourceTest {

    private static final String JOBS = "/api/jobs";

    @Inject
    JobPersistenceAdapter jobPersistenceAdapter;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanTable() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery("DELETE FROM JobEntity").executeUpdate());
    }

    @Test
    void validRequest_queueJob_returns200AndPersistsAPendingJob() {
        String body = given().contentType(ContentType.JSON).body(newJob("email", "{}", 3)).when().post(JOBS).then()
                .statusCode(200).contentType(ContentType.JSON).extract().asString();

        UUID id = UUID.fromString(body.replace("\"", ""));
        Job saved = jobPersistenceAdapter.findByIdOptional(id).orElseThrow();
        assertEquals("email", saved.type());
        assertEquals("{}", saved.payload());
        assertEquals(3, saved.maxAttempts());
        assertEquals(0, saved.currentAttempts());
        assertEquals(Pending.getInstance(), saved.status());
    }

    @Test
    void blankType_queueJob_returns400AndStoresNothing() {
        given().contentType(ContentType.JSON).body(newJob("", "{}", 3)).when().post(JOBS).then().statusCode(400)
                .contentType(ContentType.JSON).body("message", equalTo("Job type can't be empty"));

        assertEquals(0, jobPersistenceAdapter.listJobsByStatus(null).size());
    }

    @Test
    void blankPayload_queueJob_returns400() {
        given().contentType(ContentType.JSON).body(newJob("email", "  ", 3)).when().post(JOBS).then().statusCode(400)
                .body("message", equalTo("Job payload can't be empty"));
    }

    @Test
    void missingPayload_queueJob_returns400() {
        given().contentType(ContentType.JSON).body(Map.of("type", "email", "maxAttempts", 3)).when().post(JOBS).then()
                .statusCode(400).body("message", equalTo("Job payload can't be empty"));
    }

    @Test
    void zeroMaxAttempts_queueJob_returns400() {
        given().contentType(ContentType.JSON).body(newJob("email", "{}", 0)).when().post(JOBS).then().statusCode(400)
                .body("message", equalTo("Job must run at least 1 time"));
    }

    @Test
    void malformedJson_queueJob_returns400() {
        given().contentType(ContentType.JSON).body("{ not json").when().post(JOBS).then().statusCode(400);
    }

    @Test
    void existingJob_getJob_returns200WithTheJob() {
        Job job = seed(Pending.getInstance(), 0, 3);

        given().when().get(JOBS + "/" + job.id()).then().statusCode(200).contentType(ContentType.JSON)
                .body("id", equalTo(job.id().toString())).body("type", equalTo("email")).body("payload", equalTo("{}"))
                .body("status.name", equalTo("PENDING")).body("currentAttempts", equalTo(0))
                .body("maxAttempts", equalTo(3));
    }

    @Test
    void unknownId_getJob_returns404() {
        given().when().get(JOBS + "/" + UUID.randomUUID()).then().statusCode(404).contentType(ContentType.JSON)
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void statusDead_listJobs_returnsOnlyDeadJobs() {
        Job dead = seed(Dead.getInstance(), 3, 3);
        seed(Pending.getInstance(), 0, 3);
        seed(Succeeded.getInstance(), 1, 3);

        given().queryParam("status", "dead").when().get(JOBS).then().statusCode(200).body("size()", is(1))
                .body("[0].id", equalTo(dead.id().toString())).body("[0].status.name", equalTo("DEAD"));
    }

    @Test
    void statusInAnyCase_listJobs_isAccepted() {
        seed(Dead.getInstance(), 3, 3);

        given().queryParam("status", "DEAD").when().get(JOBS).then().statusCode(200).body("size()", is(1));
        given().queryParam("status", "Dead").when().get(JOBS).then().statusCode(200).body("size()", is(1));
    }

    @Test
    void noStatus_listJobs_returnsAllJobs() {
        seed(Dead.getInstance(), 3, 3);
        seed(Pending.getInstance(), 0, 3);

        given().when().get(JOBS).then().statusCode(200).body("size()", is(2));
    }

    @Test
    void noJobsWithStatus_listJobs_returnsEmptyList() {
        seed(Pending.getInstance(), 0, 3);

        given().queryParam("status", "dead").when().get(JOBS).then().statusCode(200).body("size()", is(0));
    }

    @Test
    void unknownStatus_listJobs_returns400() {
        given().queryParam("status", "bogus").when().get(JOBS).then().statusCode(400).body("message",
                equalTo("Unknown job status: BOGUS"));
    }

    @Test
    void deadJob_retry_returns200AndJobIsPendingAgain() {
        Job dead = seed(Dead.getInstance(), 3, 3);
        Instant before = Instant.now().minusSeconds(1);

        given().when().post(JOBS + "/" + dead.id() + "/retry").then().statusCode(200);

        Job requeued = jobPersistenceAdapter.findByIdOptional(dead.id()).orElseThrow();
        assertEquals(Pending.getInstance(), requeued.status());
        assertEquals(0, requeued.currentAttempts());
        assertFalse(requeued.runAfter().isBefore(before), "run_after should be reset to about now");
        assertFalse(requeued.runAfter().isAfter(Instant.now()), "run_after must not be in the future");
    }

    @Test
    void jobThatIsNotDead_retry_returns404AndLeavesTheJobUntouched() {
        Job pending = seed(Pending.getInstance(), 1, 3);

        given().when().post(JOBS + "/" + pending.id() + "/retry").then().statusCode(404).body("message",
                containsString(pending.id().toString()));

        Job unchanged = jobPersistenceAdapter.findByIdOptional(pending.id()).orElseThrow();
        assertEquals(Pending.getInstance(), unchanged.status());
        assertEquals(1, unchanged.currentAttempts());
    }

    @Test
    void unknownId_retry_returns404() {
        UUID unknown = UUID.randomUUID();

        given().when().post(JOBS + "/" + unknown + "/retry").then().statusCode(404).body("message",
                containsString(unknown.toString()));
    }

    private static Map<String, Object> newJob(String type, String payload, int maxAttempts) {
        return Map.of("type", type, "payload", payload, "maxAttempts", maxAttempts);
    }

    private Job seed(JobStatus status, int currentAttempts, int maxAttempts) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return jobPersistenceAdapter
                .store(new Job(null, "email", "{}", status, currentAttempts, maxAttempts, now, now, now));
    }
}
