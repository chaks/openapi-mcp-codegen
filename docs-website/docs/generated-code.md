---
sidebar_position: 4
---

# Generated Code Structure

## Overview

The generator produces three layers of code: domain classes, REST clients, and MCP tool wrappers.

## Domain Layer

Data classes with Jackson/JSON-B annotations for serialization/deserialization.

### Example: Pet.kt

```kotlin
package io.swagger.petstore.domain

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import jakarta.json.bind.annotation.JsonbProperty

/**
 * A pet for sale in the pet store
 */
@kotlinx.serialization.Serializable
data class Pet(
    @JsonProperty("id")
    @JsonbProperty("id")
    val id: Long? = null,

    @JsonProperty("name")
    @JsonbProperty("name")
    val name: String,

    @JsonProperty("category")
    @JsonbProperty("category")
    val category: Category? = null,

    @JsonProperty("status")
    @JsonbProperty("status")
    val status: String? = null
)
```

## Client Layer

REST client interfaces using MicroProfile Rest Client annotations.

### Example: PetstoreClient.kt

```kotlin
package io.swagger.petstore.client

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.annotation.RegisterRestClient

@RegisterRestClient(configKey = "petstore-api")
interface PetstoreClient {

    @GET("/pet/findByStatus")
    fun findPetsByStatus(
        @QueryParam("status") status: List<String>
    ): List<Pet>

    @GET("/pet/{petId}")
    fun getPetById(
        @PathParam("petId") petId: Long
    ): Pet
}
```

## MCP Tool Layer

Quarkus MCP server `@Tool` and `@ToolArg` annotated wrappers for AI/LLM integration.

### Example: PetstoreTools.kt

```kotlin
package io.swagger.petstore.tool

import io.quarkiverse.mcp.server.Tool
import io.quarkiverse.mcp.server.ToolArg
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class PetstoreTools {

    @RestClient
    lateinit var client: PetstoreClient

    @Tool("GET /pet/findByStatus - Finds Pets by status")
    fun findPetsByStatus(
        @ToolArg("The status values to filter by")
        status: List<String>
    ): List<Pet> {
        return client.findPetsByStatus(status)
    }

    @Tool("GET /pet/{petId} - Find pet by ID")
    fun getPetById(
        @ToolArg("ID of pet to return")
        petId: Long
    ): Pet {
        return client.getPetById(petId)
    }
}
```

## Architecture

```
CLI (Main.kt)
  └─> CliCommand.parse()
      └─> OpenApiParser.parse(inputFile)
          └─> CodeGenerator.generate(parsedModel)
              ├─> DomainGenerator.generate() → domain classes
              ├─> ClientGenerator.generate() → REST client interfaces
              ├─> ToolGenerator.generate() → MCP tool wrappers
              └─> Generate Gradle build file
```

## Next Steps

- [OpenAPI Support](./openapi-support) - See what OpenAPI features are supported