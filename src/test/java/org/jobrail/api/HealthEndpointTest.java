/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointTest {

    @Test
    void live_returnsUp() {
        given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void ready_returnsUp_andIncludesDatabaseCheck() {
        given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP")).body("checks.name",
                hasItem("Database connections health check"));
    }
}
