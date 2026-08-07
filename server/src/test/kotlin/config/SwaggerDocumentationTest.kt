package com.revio.server.config

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Confirms `openapi/documentation.yaml` — extended in Pas 7 with GET /challenges/me,
 * GET /challenges/{id}, and the extended ChallengeContributionDTO — (a) is well-formed and
 * actually loads through the same [io.ktor.server.plugins.swagger.swaggerUI]/
 * [io.ktor.server.plugins.openapi.openAPI] setup Application.kt's configureSwagger() uses,
 * without booting the full app (no DB needed), and (b) contains the 3 routes' final shape.
 *
 * [io.ktor.server.plugins.openapi.openAPI]'s `/openapi` route renders an interactive HTML docs
 * page (it reads and parses the YAML server-side to build that page, so a malformed file still
 * fails loudly there) rather than serving the raw spec back — so the *content* assertions read
 * the same classpath resource directly instead of scraping rendered HTML/JS.
 */
class SwaggerDocumentationTest {

    private fun documentationYaml(): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("openapi/documentation.yaml")) {
            "openapi/documentation.yaml not found on the test classpath"
        }.bufferedReader().readText()

    @Test
    fun `swagger UI page loads successfully from the documentation file`() = testApplication {
        application {
            install(RoutingRoot)
            routing { swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") }
        }

        val response = client.get("/swagger")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `openapi docs page loads successfully from the same documentation file`() = testApplication {
        application {
            install(RoutingRoot)
            routing { openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml") }
        }

        val response = client.get("/openapi")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `documentation file lists GET challenges me with its final response shape`() {
        val yaml = documentationYaml()

        assertTrue(yaml.contains("/challenges/me:"), "spec must list GET /challenges/me")
        assertTrue(yaml.contains("MyChallengesDTO"), "spec must reference the final MyChallengesDTO shape")
        assertTrue(yaml.contains("ChallengeHistoryItemDTO"))
        assertTrue(yaml.contains("ChallengeSummaryDTO"))
    }

    @Test
    fun `documentation file lists GET challenges id as its own path, not just challenges id progress`() {
        val yaml = documentationYaml()

        assertTrue(yaml.contains("/challenges/{id}:"), "spec must list GET /challenges/{id} as its own path")
        assertTrue(yaml.contains("/challenges/{id}/progress:"), "spec must still list the existing GET /challenges/{id}/progress")
    }

    @Test
    fun `documentation file lists the extended challenges id progress contribution fields`() {
        val yaml = documentationYaml()
        val contributionDtoBlock = yaml.substringAfter("ChallengeContributionDTO:").substringBefore("\n\n")

        assertTrue(contributionDtoBlock.contains("imageUrl"))
        assertTrue(contributionDtoBlock.contains("carBrand"))
        assertTrue(contributionDtoBlock.contains("carModel"))
    }
}
