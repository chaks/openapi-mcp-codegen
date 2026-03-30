# OpenAPI MCP Codegen

A CLI tool that parses OpenAPI 3.0/3.1 YAML specifications and generates a structured Quarkus-based SDK with MCP (Model Context Protocol) Toolset integration.

## Overview

This tool automates the generation of:
- **Domain Layer**: Data classes with Jackson/JSON-B annotations
- **Client Layer**: REST client interfaces using MicroProfile Rest Client
- **MCP Tool Layer**: Quarkus MCP server `@Tool` and `@ToolArg` annotated wrappers for AI/LLM integration

## Features

- Parses OpenAPI 3.0 and 3.1 specifications
- Generates type-safe Kotlin code using KotlinPoet
- Creates Quarkus-ready REST clients
- Generates MCP tools with `@Tool` and `@ToolArg` annotations for AI agent integration
- Includes Gradle wrapper and build configuration in generated output
- Optional auto-compilation of generated code
- Selective JAR packaging (domain+client only, excludes tools)
- **Schema Resilience**: Handles malformed, incomplete, and non-compliant OpenAPI specs with safe defaults
- **Circular Reference Detection**: Detects and gracefully handles circular schema references
- **Type Inference**: Automatically infers missing type fields from schema structure
- **Identifier Sanitization**: Converts any OpenAPI identifier to valid Kotlin identifiers

## Requirements

- JDK 17+
- Gradle 8+

## Building the Generator

### For Development (Fast Build)

```bash
./gradlew build
```

This creates the executable JAR at `build/quarkus-app/quarkus-run.jar` (requires the `lib/` directory alongside).

### For Distribution (Single Binary)

```bash
./gradlew clean quarkusBuild -Dquarkus.package.type=uber-jar
```

This creates a single, distributable JAR with all dependencies bundled at `build/openapi-mcp-codegen-1.0.0-runner.jar` (~33MB). Use this for distribution.

## Usage

### Basic Usage

```bash
java -jar build/quarkus-app/quarkus-run.jar -i <input.yaml> -o <output-dir> -r <root-package>
```

### Example

Generate code for the Petstore API:

```bash
java -jar build/quarkus-app/quarkus-run.jar --input examples/petstore.yaml --output ./generated --root-package io.swagger.petstore
```

### CLI Options

| Option           | Short | Description                                   | Required                  |
|------------------|-------|-----------------------------------------------|---------------------------|
| `--input`        | `-i`  | Path to the OpenAPI YAML file                 | Yes                       |
| `--output`       | `-o`  | Output directory for generated code           | No (default: ./generated) |
| `--root-package` | `-r`  | Root package name (e.g., io.swagger.petstore) | Yes                       |
| `--verbose`      | `-v`  | Enable verbose output                         | No                        |
| `--compile`      | `-c`  | Compile the generated code after generation   | No                        |

### With Verbose Output

```bash
java -jar build/quarkus-app/quarkus-run.jar -i examples/petstore.yaml -o ./generated -r io.swagger.petstore -v
```

### With Auto-Compilation

```bash
java -jar build/quarkus-app/quarkus-run.jar -i examples/petstore.yaml -o ./generated -r io.swagger.petstore -c
```

Compiles the generated code after generation using the Gradle wrapper that is included in the output directory.

## Schema Resilience & Hardening

The code generator includes robust handling for malformed, incomplete, or non-compliant OpenAPI specifications.

### Safe Defaults Applied

| Issue | Behavior | Default Applied |
|-------|----------|-----------------|
| Missing `info` section | Auto-create | `title: "Unnamed API"`, `version: "1.0.0"` |
| Missing `type` field | Infer from structure | `properties` → `object`, `items` → `array`, `enum` → `string` |
| Unknown type | Log warning, continue | `Any` |
| Null schema | Log warning, continue | Empty object schema |
| Array without `items` | Log warning, continue | `items: Any` |
| Missing `paths` | Auto-create | Empty paths object |
| Missing `components` | Auto-create | Empty schemas map |
| Broken `$ref` | Log error, continue | Graceful handling with safe defaults |
| Circular references | Detect and warn | Safe placeholder to prevent stack overflow |
| Empty `types` array | Log warning, default | `object` |
| Invalid identifier names | Sanitize | Valid Kotlin identifier |

### Circular Reference Detection

The generator detects both direct and indirect circular references:

```yaml
# Direct: Self-referencing schema
Node:
  type: object
  properties:
    next:
      $ref: '#/components/schemas/Node'

# Indirect: A → B → A cycle
SchemaA:
  type: object
  properties:
    b:
      $ref: '#/components/schemas/SchemaB'
SchemaB:
  type: object
  properties:
    a:
      $ref: '#/components/schemas/SchemaA'
```

When detected, the generator:
1. Logs a warning with the full cycle path
2. Creates a safe placeholder schema instead of crashing
3. Continues processing remaining schemas

### Type Inference

When `type` is missing, the generator infers from structure:

```yaml
# No 'type' but has 'properties' → inferred as 'object'
MySchema:
  properties:
    name:
      type: string

# No 'type' but has 'items' → inferred as 'array'
MyArray:
  items:
    type: string

# No 'type', no structure → defaults to 'object'
EmptySchema: {}
```

### Identifier Sanitization

All OpenAPI identifiers are converted to valid Kotlin identifiers:

| Input | Output |
|-------|--------|
| `user-name` | `userName` |
| `User Name` | `UserName` |
| `123name` | `_123name` |
| `class` (keyword) | `` `class` `` |
| `user@name` | `userName` |
| `__test__` | `__test__` |

### Validation Warnings

The generator logs warnings for non-compliant specs:

```
[SpecNormalizer] Schema 'MySchema' missing 'type' field - inferred as 'object' from structure
[SpecNormalizer] Referrer.missing has broken reference '#/components/schemas/NonExistent'
[SpecNormalizer] Circular reference detected: SchemaA -> SchemaB -> SchemaA
[SpecNormalizer] Schema 'UnknownType' has unknown type 'invalid_xyz' - defaulting to 'object'
```


## Generated Structure

For a root package of `io.swagger.petstore`, the generator creates:

```
generated/
├── build.gradle.kts              # Gradle build configuration
├── settings.gradle.kts           # Gradle settings file
├── gradlew                       # Gradle wrapper script (Unix)
├── gradlew.bat                   # Gradle wrapper script (Windows)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar    # Gradle wrapper JAR
│       └── gradle-wrapper.properties
├── src/main/kotlin/
│   └── io/swagger/petstore/
│       ├── domain/               # Data classes
│       │   ├── Pet.kt
│       │   ├── Category.kt
│       │   └── Order.kt
│       ├── client/               # REST client interface
│       │   └── PetstoreClient.kt
│       └── tool/                 # MCP tool wrappers
│           └── PetstoreTools.kt
└── src/main/resources/
    └── application.properties    # Quarkus configuration
```

## Building the Generated Code

```bash
cd generated
./gradlew build
```

### Building Selective JAR (Domain + Client only)

```bash
./gradlew domainClientJar
```

This creates a JAR file containing only the `domain` and `client` packages, excluding the `tool` package.

## OpenAPI Specification Support

### Supported Features

- Schema definitions (`components/schemas`)
- Path definitions with HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Parameters (path, query, header, cookie)
- Request bodies
- Responses with schema references
- Arrays and nested objects
- Enum values
- Required/optional fields

### Type Mapping

| OpenAPI Type | Format    | Kotlin Type |
|--------------|-----------|-------------|
| string       | -         | `String`    |
| string       | date-time | `String`    |
| string       | uuid      | `String`    |
| integer      | int32     | `Int`       |
| integer      | int64     | `Long`      |
| number       | float     | `Float`     |
| number       | double    | `Double`    |
| boolean      | -         | `Boolean`   |
| array        | -         | `List<T>`   |
| object       | -         | Data class  |

## Example Generated Code

### Domain Class (Pet.kt)

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

### REST Client Interface (PetstoreClient.kt)

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
}
```

### MCP Tool Wrapper (PetstoreTools.kt)

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
}
```

## Architecture

```
CLI (Main.kt)
  └─> CliCommand.parse()
      └─> OpenApiParser.parse(inputFile)
          ├─> SpecNormalizer.normalize() [NEW]
          │   ├─ Validate structure
          │   ├─ Apply safe defaults
          │   ├─ Detect circular refs
          │   └─ Log warnings/errors
          └─> CodeGenerator.generate(parsedModel)
              ├─> DomainGenerator.generate() → domain classes
              ├─> ClientGenerator.generate() → REST client interfaces
              ├─> ToolGenerator.generate() → MCP tool wrappers
              └─> Generate Gradle build file
```

### Defense-in-Depth Layers

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| 1 | `SpecNormalizer` | Validate & normalize spec before processing |
| 2 | `SchemaExtractor` | Safe schema extraction with cycle detection |
| 3 | `TypeMapper` | Safe type mapping with unknown type fallback |
| 4 | `TypeResolver` | Defensive type resolution with try/catch |


