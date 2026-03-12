package io.github.chaks.openapi2mcp

import io.github.chaks.openapi2mcp.parser.OpenApiParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class OpenApiParserTest {

    @Test
    fun `should parse valid OpenAPI specification`() {
        val parser = OpenApiParser()
        val examplePath = Paths.get("examples/petstore.yaml")

        // Skip test if example file doesn't exist
        if (!examplePath.toFile().exists()) {
            println("Skipping test: Example file not found")
            return
        }

        val result = parser.parse(examplePath)

        assertNotNull(result)
        assertEquals("3.0.4", result.openapiVersion)
        assertEquals("Swagger Petstore - OpenAPI 3.0", result.info.title)
        assertEquals("1.0.27", result.info.version)

        assertTrue(result.schemas.isNotEmpty())
        assertTrue(result.paths.isNotEmpty())
    }

    @Test
    fun `should extract schemas correctly`() {
        val parser = OpenApiParser()
        val examplePath = Paths.get("examples/petstore.yaml")

        // Skip test if example file doesn't exist
        if (!examplePath.toFile().exists()) {
            println("Skipping test: Example file not found")
            return
        }

        val result = parser.parse(examplePath)

        assertTrue(result.schemas.containsKey("Pet"))
        val pet = result.schemas["Pet"]!!
        assertEquals("Pet", pet.name)
        assertTrue(pet.properties.containsKey("name"))
        assertTrue(pet.properties.containsKey("photoUrls"))
        assertTrue(pet.required.contains("name"))
    }

    @Test
    fun `should extract paths correctly`() {
        val parser = OpenApiParser()
        val examplePath = Paths.get("examples/petstore.yaml")

        // Skip test if example file doesn't exist
        if (!examplePath.toFile().exists()) {
            println("Skipping test: Example file not found")
            return
        }

        val result = parser.parse(examplePath)

        val findByStatusPath = result.paths.find {
            it.path == "/pet/findByStatus" && it.method == "GET"
        }

        assertNotNull(findByStatusPath)
        assertEquals("findPetsByStatus", findByStatusPath?.operationId)
        assertTrue(findByStatusPath?.parameters?.isNotEmpty() == true)

        val statusParam = findByStatusPath?.parameters?.find { it.name == "status" }
        assertNotNull(statusParam)
        assertEquals("query", statusParam?.`in`)
        assertTrue(statusParam?.required == true)
    }
}