# Development Guide

Guide for contributing to and extending OpenAPI MCP Codegen.

## Setting Up Development Environment

### Prerequisites

- JDK 17+
- Gradle 8+
- IDE with Kotlin support (IntelliJ IDEA recommended)

### Clone and Build

```bash
# Clone the repository
git clone https://github.com/yourusername/openapi-mcp-codegen.git
cd openapi-mcp-codegen

# Build the project
./gradlew build

# Run tests
./gradlew test
```

### IDE Setup

#### IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. The IDE will automatically detect the Gradle project
3. Wait for Gradle sync to complete
4. Enable Kotlin plugin (usually enabled by default)

#### VS Code

1. Install the Kotlin extension
2. Install the Gradle extension
3. Open the project folder
4. The workspace will be configured automatically

## Project Structure

```
openapi-mcp-codegen/
├── src/
│   ├── main/
│   │   ├── kotlin/io/kritrimabuddhi/codegen/openapi2mcp/
│   │   │   ├── Main.kt                 # Application entry point
│   │   │   ├── cli/
│   │   │   │   ├── CliCommand.kt       # CLI command handler
│   │   │   │   └── CliOptions.kt       # Configuration wrapper
│   │   │   ├── parser/
│   │   │   │   ├── OpenApiParser.kt    # OpenAPI parsing logic
│   │   │   │   └── model/               # Internal data models
│   │   │   ├── generator/
│   │   │   │   ├── CodeGenerator.kt    # Main orchestrator
│   │   │   │   ├── DomainGenerator.kt  # Domain layer generation
│   │   │   │   ├── ClientGenerator.kt  # Client layer generation
│   │   │   │   └── ToolGenerator.kt    # Tool layer generation
│   │   │   └── util/
│   │   │       ├── TypeMapper.kt       # Type mapping utilities
│   │   │       └── StringExtensions.kt # String extensions
│   │   └── resources/
│   └── test/
│       └── kotlin/io/kritrimabuddhi/codegen/openapi2mcp/
│           └── ...                     # Test files
├── examples/
│   └── petstore.yaml                   # Sample OpenAPI spec
├── wiki/                               # Wiki documentation
├── build.gradle.kts                    # Gradle build configuration
└── README.md
```

## Development Workflow

### Running the Generator

```bash
# Run with default options
./gradlew run

# Run with custom options
./gradlew run --args="-i examples/petstore.yaml -o ./test-generated -r io.swagger.test"

# Run with verbose output
./gradlew run --args="-i examples/petstore.yaml -v"
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "*OpenApiParserTest"

# Run tests with coverage
./gradlew test jacocoTestReport
```

### Code Formatting

The project uses ktlint for code formatting:

```bash
# Format code
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck
```

## Adding New Features

### Adding a New CLI Option

1. **Update CliCommand.kt**:

```kotlin
@CommandLine.Command(
    name = "openapi-mcp-codegen",
    mixinStandardHelpOptions = true
)
class CliCommand : Runnable {

    @CommandLine.Option(names = ["-i", "--input"], description = ["Input file"])
    var inputFile: File? = null

    // Add your new option here
    @CommandLine.Option(names = ["--custom-option"], description = ["Custom option"])
    var customOption: String? = null

    override fun run() {
        val options = CliOptions(
            inputFile = inputFile,
            customOption = customOption  // Pass the new option
        )
        // ...
    }
}
```

2. **Update CliOptions.kt**:

```kotlin
data class CliOptions(
    val inputFile: File?,
    val customOption: String? = null,  // Add the new option
    // Derive additional properties
    val packageName: String = rootPackage ?: ""
) {
    init {
        require(inputFile != null) { "Input file is required" }
    }
}
```

### Extending Type Mapping

To add custom type mappings, modify `TypeMapper.kt`:

```kotlin
object TypeMapper {

    fun mapType(schema: Schema<*>): TypeName {
        return when (schema.type) {
            "string" -> mapStringType(schema)
            "integer" -> mapIntegerType(schema)
            "number" -> mapNumberType(schema)
            "boolean" -> BOOLEAN
            "array" -> mapArrayType(schema)
            "object" -> mapObjectType(schema)
            else -> ANY
        }
    }

    private fun mapStringType(schema: Schema<*>): TypeName {
        return when (schema.format) {
            "date-time" -> STRING  // Could map to Instant
            "uuid" -> UUID::class.asTypeName()  // Add UUID support
            else -> STRING
        }
    }

    // Add more custom mapping functions as needed
}
```

### Adding a New Generator

To add support for generating a new layer:

1. **Create a new generator class**:

```kotlin
@ApplicationScoped
class CustomGenerator(
    private val typeMapper: TypeMapper
) {

    fun generate(
        model: ParsedOpenApi,
        options: CliOptions
    ): List<FileSpec> {
        val files = mutableListOf<FileSpec>()

        // Generate your custom files
        model.schemas.forEach { (_, schema) ->
            files.add(generateCustomFile(schema, options))
        }

        return files
    }

    private fun generateCustomFile(
        schema: SchemaModel,
        options: CliOptions
    ): FileSpec {
        // Use KotlinPoet to generate your custom code
        val packageSpec = "${options.rootPackage}.custom"

        return FileSpec.builder(packageSpec, schema.name)
            .addType(buildCustomType(schema))
            .build()
    }

    private fun buildCustomType(schema: SchemaModel): TypeSpec {
        // Build your custom type using KotlinPoet
        return TypeSpec.classBuilder(schema.name + "Custom")
            .addModifiers(Kotlin.DATA)
            .build()
    }
}
```

2. **Integrate with CodeGenerator**:

```kotlin
@ApplicationScoped
class CodeGenerator(
    private val domainGenerator: DomainGenerator,
    private val clientGenerator: ClientGenerator,
    private val toolGenerator: ToolGenerator,
    private val customGenerator: CustomGenerator  // Inject your new generator
) {

    fun generate(options: CliOptions): GenerationResult {
        val parser = OpenApiParser()
        val model = parser.parse(options.inputFile)

        val domainFiles = domainGenerator.generate(model, options)
        val clientFiles = clientGenerator.generate(model, options)
        val toolFiles = toolGenerator.generate(model, options)
        val customFiles = customGenerator.generate(model, options)  // Generate custom files

        // Write all files
        return GenerationResult(
            files = domainFiles + clientFiles + toolFiles + customFiles
        )
    }
}
```

## Writing Tests

### Unit Tests

```kotlin
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TypeMapperTest {

    @Test
    fun `should map integer type to Int`() {
        val schema = IntegerSchema().apply {
            format = "int32"
        }
        val result = TypeMapper.mapType(schema)
        assertEquals(INT, result)
    }

    @Test
    fun `should map array type to List`() {
        val schema = ArraySchema<Any>().apply {
            items = StringSchema()
        }
        val result = TypeMapper.mapType(schema)
        assertTrue(result.toString().contains("List"))
    }
}
```

### Integration Tests

```kotlin
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

@QuarkusTest
class CodeGeneratorIntegrationTest {

    @Inject
    lateinit var codeGenerator: CodeGenerator

    @Test
    fun `should generate code from valid OpenAPI spec`() {
        val options = CliOptions(
            inputFile = File("examples/petstore.yaml"),
            rootPackage = "io.swagger.test"
        )

        val result = codeGenerator.generate(options)
        assertNotNull(result)
        assertTrue(result.files.isNotEmpty())
    }
}
```

## Debugging

### Enable Debug Logging

Add to `src/main/resources/application.properties`:

```properties
quarkus.log.level=DEBUG
quarkus.log.category."io.kritrimabuddhi".level=DEBUG
```

### Run in Debug Mode

```bash
./gradlew run --debug-jvm --args="-i examples/petstore.yaml -v"
```

## Code Style Guidelines

### Kotlin

- Use data classes for immutable data
- Prefer `val` over `var`
- Use `when` expressions instead of long `if-else` chains
- Add KDoc comments for public functions and classes

### Example

```kotlin
/**
 * Maps OpenAPI schema types to Kotlin type names.
 *
 * @param schema The OpenAPI schema to map
 * @return The corresponding Kotlin type name
 */
fun mapType(schema: Schema<*>): TypeName {
    return when (schema.type) {
        "string" -> STRING
        "integer" -> when (schema.format) {
            "int64" -> LONG
            else -> INT
        }
        else -> ANY
    }
}
```

## Submitting Changes

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/my-feature`
3. **Make your changes**
4. **Run tests**: `./gradlew test`
5. **Format code**: `./gradlew ktlintFormat`
6. **Commit your changes**: `git commit -m "Add my feature"`
7. **Push to your fork**: `git push origin feature/my-feature`
8. **Create a pull request**

## Common Issues

### Build Failures

```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

### Test Failures

```bash
# Run tests with detailed output
./gradlew test --info

# Run specific test class
./gradlew test --tests "*YourTestClass"
```

### IDE Issues

If you encounter IDE-related issues:

1. Invalidate caches and restart
2. Reimport the Gradle project
3. Ensure JDK 17 is selected as project SDK

## Additional Resources

- [KotlinPoet Documentation](https://square.github.io/kotlinpoet/)
- [Swagger Parser Documentation](https://github.com/swagger-api/swagger-parser)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Kotlin Documentation](https://kotlinlang.org/docs/)

## Related Documentation

- [Architecture](Architecture) - Deep dive into the codebase
- [Getting Started](Getting-Started) - Installation and first steps
- [Examples](Examples) - Real-world usage examples