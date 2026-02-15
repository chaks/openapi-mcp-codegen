# Architecture

Deep dive into the architecture and design of OpenAPI MCP Codegen.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Interface                        │
│                        (Quarkus + Picocli)                   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ parse()
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     OpenAPI Parser                           │
│                    (Swagger Parser)                          │
│                                                               │
│  - Reads YAML file                                           │
│  - Resolves $ref references                                   │
│  - Extracts schemas, paths, parameters, responses            │
│  - Converts to internal model                                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ parsedModel
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      Code Generator                          │
│                    (Orchestrator)                            │
│                                                               │
│  Coordinates generation across all layers                  │
└───────────────┬───────────────────────────┬────────────────┘
                │                           │
                │                           │
    ┌───────────▼───────────┐   ┌───────────▼───────────┐
    │   Domain Generator    │   │   Client Generator    │
    │                       │   │                       │
    │ - Data classes        │   │ - REST client iface   │
    │ - Annotations         │   │ - JAX-RS annotations  │
    │ - Type mapping        │   │ - Parameter mapping   │
    └───────────────────────┘   └───────────┬───────────┘
                                            │
                                ┌───────────▼───────────┐
                                │   Tool Generator      │
                                │                       │
                                │ - @Tool annotations   │
                                │ - CDI beans           │
                                │ - Client injection    │
                                └───────────────────────┘
```

## Component Overview

### 1. CLI Interface

**Location**: `src/main/kotlin/io/kritrimabuddhi/codegen/openapi2mcp/`

**Components**:
- `Main.kt`: Quarkus application entry point
- `cli/CliCommand.kt`: Picocli command handler

**Responsibilities**:
- Parse command-line arguments
- Validate inputs
- Coordinate the generation process
- Provide user feedback

### 2. OpenAPI Parser

**Location**: `src/main/kotlin/io/kritrimabuddhi/codegen/openapi2mcp/parser/`

**Components**:
- `OpenApiParser.kt`: Main parsing logic
- `model/ParsedOpenApi.kt`: Internal data models

**Responsibilities**:
- Parse OpenAPI YAML specifications
- Resolve `$ref` references
- Extract all schemas, paths, and operations
- Convert Swagger models to internal representation

**Internal Model**:
```kotlin
data class ParsedOpenApi(
    val info: ApiInfo,
    val schemas: Map<String, SchemaModel>,
    val paths: Map<String, PathModel>,
    val components: ComponentSchemas
)

data class SchemaModel(
    val name: String,
    val type: String?,
    val properties: Map<String, PropertyInfo>,
    val required: List<String>,
    val items: SchemaModel?,
    val ref: String?
)
```

### 3. Code Generator

**Location**: `src/main/kotlin/io/kritrimabuddhi/codegen/openapi2mcp/generator/`

**Components**:
- `CodeGenerator.kt`: Main orchestrator
- `DomainGenerator.kt`: Domain layer generation
- `ClientGenerator.kt`: Client layer generation
- `ToolGenerator.kt`: MCP tool layer generation

**Responsibilities**:
- Coordinate generation across all layers
- Generate Kotlin source files using KotlinPoet
- Create Gradle build configuration
- Generate Quarkus configuration

### 4. Utilities

**Location**: `src/main/kotlin/io/kritrimabuddhi/codegen/openapi2mcp/util/`

**Components**:
- `TypeMapper.kt`: OpenAPI to Kotlin type conversion
- `CliOptions.kt`: Configuration wrapper

**Responsibilities**:
- Map OpenAPI types to Kotlin types
- Handle nullability
- Derive package and class names
- Provide configuration helpers

## Data Flow

```
Input: OpenAPI YAML
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  1. Read and Parse YAML                              │
│     - Swagger Parser reads the file                  │
│     - Resolves all $ref references                   │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  2. Convert to Internal Model                       │
│     - Extract schemas as SchemaModel                 │
│     - Extract paths as PathModel                     │
│     - Extract parameters as ParameterInfo           │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  3. Generate Domain Layer                          │
│     - Create data class spec for each schema         │
│     - Add serialization annotations                  │
│     - Handle nested types and arrays                 │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  4. Generate Client Layer                           │
│     - Create REST client interface                  │
│     - Add JAX-RS annotations                        │
│     - Map parameters correctly                       │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  5. Generate Tool Layer                             │
│     - Create CDI bean class                         │
│     - Add @Tool annotations                         │
│     - Inject REST client                            │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  6. Generate Build Configuration                    │
│     - Create build.gradle.kts                        │
│     - Add dependencies                               │
│     - Create application.properties                  │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
Output: Complete Quarkus SDK
```

## Type Mapping Strategy

### OpenAPI to Kotlin Type Mapping

```kotlin
// TypeMapper.kt
fun mapType(schema: Schema<*>): TypeName {
    return when (schema.type) {
        "string" -> when (schema.format) {
            "int64" -> LONG
            "int32" -> INT
            "float" -> FLOAT
            "double" -> DOUBLE
            else -> STRING
        }
        "integer" -> when (schema.format) {
            "int64" -> LONG
            else -> INT
        }
        "number" -> when (schema.format) {
            "float" -> FLOAT
            else -> DOUBLE
        }
        "boolean" -> BOOLEAN
        "array" -> LIST.parameterizedBy(mapType(schema.items))
        "object" -> ClassName.bestGuess(resolveRef(schema.`$ref`))
        else -> ANY
    }
}
```

### Nullability Handling

```kotlin
fun mapProperty(
    schema: Schema<*>,
    propertyName: String,
    requiredFields: List<String>
): PropertySpec {
    val typeName = mapType(schema)
    val isNullable = !requiredFields.contains(propertyName)

    return PropertySpec.builder(
        propertyName,
        if (isNullable) typeName.copy(nullable = true) else typeName
    ).build()
}
```

## Code Generation Strategy

### Using KotlinPoet

The generator uses KotlinPoet for type-safe code generation:

```kotlin
// Example: Generating a data class
fun generateDataClass(schema: SchemaModel): TypeSpec {
    val properties = schema.properties.map { (name, info) ->
        PropertySpec.builder(
            name.sanitize(),
            mapPropertyType(info)
        )
            .addAnnotation(JsonProperty::class.java)
            .addAnnotation(JsonbProperty::class.java)
            .initializer(if (info.required) "" else "null")
            .build()
    }

    return TypeSpec.classBuilder(schema.name)
        .addModifiers(Kotlin.DATA)
        .addAnnotation(Serializable::class.java)
        .addProperties(properties)
        .build()
}
```

### Annotation Strategy

Multiple serialization frameworks are supported:

| Framework | Annotation | Purpose |
|-----------|------------|---------|
| Jackson | `@JsonProperty` | JSON field name mapping |
| Jackson | `@JsonInclude` | Null handling |
| Jackson | `@JsonPropertyOrder` | Field order |
| JSON-B | `@JsonbProperty` | JSON-B compatibility |
| Kotlin Serialization | `@Serializable` | Kotlinx serialization |

## Design Patterns

### 1. Builder Pattern

Used in KotlinPoet for building code structures:

```kotlin
TypeSpec.classBuilder("MyClass")
    .addModifiers(Kotlin.DATA)
    .addProperty("id", INT)
    .build()
```

### 2. Strategy Pattern

Different generators for different layers:

```kotlin
interface Generator {
    fun generate(model: ParsedOpenApi): List<FileSpec>
}

class DomainGenerator : Generator { ... }
class ClientGenerator : Generator { ... }
class ToolGenerator : Generator { ... }
```

### 3. Dependency Injection

Quarkus CDI for all components:

```kotlin
@ApplicationScoped
class CodeGenerator(
    private val domainGenerator: DomainGenerator,
    private val clientGenerator: ClientGenerator,
    private val toolGenerator: ToolGenerator
) {
    // ...
}
```

## Extension Points

### Customizing Type Mapping

To customize type mapping, modify `TypeMapper.kt`:

```kotlin
fun mapType(schema: Schema<*>): TypeName {
    // Add custom type mappings here
    return when (schema.format) {
        "date-time" -> Instant::class.asTypeName()  // Custom mapping
        else -> defaultMapping(schema)
    }
}
```

### Adding Custom Annotations

To add custom annotations to generated classes, modify the respective generator:

```kotlin
fun generateDataClass(schema: SchemaModel): TypeSpec {
    return TypeSpec.classBuilder(schema.name)
        .addAnnotation(Serializable::class.java)
        .addAnnotation(YourCustomAnnotation::class.java)  // Add here
        .addProperties(properties)
        .build()
}
```

### Custom Build Configuration

Modify the build configuration generation in `CodeGenerator.kt`:

```kotlin
fun generateBuildConfig(): String {
    return """
        plugins {
            kotlin("jvm") version "2.1.0"
            id("io.quarkus") version "3.15.3"
        }
        // Add your custom dependencies
    """.trimIndent()
}
```

## Performance Considerations

### Parsing Phase

- Uses Swagger Parser for efficient OpenAPI parsing
- Resolves all references in memory
- Creates immutable internal models

### Generation Phase

- KotlinPoet is optimized for performance
- File I/O is batched
- No unnecessary string concatenation

### Memory Usage

- Internal models are kept lightweight
- Large specs are processed incrementally
- Generated code is written directly to disk

## Testing Strategy

### Unit Tests

- Test type mapping logic
- Test annotation generation
- Test property mapping

### Integration Tests

- Test with real OpenAPI specs
- Verify generated code compiles
- Check Quarkus compatibility

### Example Tests

```kotlin
@Test
fun `should map string type to String`() {
    val schema = StringSchema()
    val result = TypeMapper.mapType(schema)
    assertEquals(STRING, result)
}

@Test
fun `should generate valid data class`() {
    val schema = SchemaModel(
        name = "User",
        type = "object",
        properties = mapOf("id" to PropertyInfo("id", "integer"))
    )
    val result = DomainGenerator.generateDataClass(schema)
    assertTrue(result.modifiers.contains(Kotlin.DATA))
}
```

## Related Documentation

- [Getting Started](Getting-Started) - Installation and first steps
- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [Development Guide](Development-Guide) - Contributing to the project
