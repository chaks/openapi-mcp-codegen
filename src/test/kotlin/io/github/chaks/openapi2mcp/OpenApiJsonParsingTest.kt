package io.github.chaks.openapi2mcp

import io.github.chaks.openapi2mcp.parser.SwaggerOpenApiParser
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@QuarkusTest
class OpenApiJsonParsingTest {

  @Inject
  lateinit var parser: SwaggerOpenApiParser

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `should parse an OpenAPI JSON specification`() {
    val specification = tempDir.resolve("openapi.json")
    Files.writeString(
      specification,
      """
      {
        "openapi": "3.1.0",
        "info": {
          "title": "JSON Example API",
          "version": "1.0.0"
        },
        "paths": {
          "/status": {
            "get": {
              "operationId": "getStatus",
              "responses": {
                "200": {
                  "description": "OK"
                }
              }
            }
          }
        }
      }
      """.trimIndent()
    )

    val result = parser.parse(specification)

    assertEquals("3.1.0", result.openapiVersion)
    assertEquals("JSON Example API", result.info.title)
    assertTrue(result.paths.any { it.path == "/status" && it.method == "GET" })
  }
}
