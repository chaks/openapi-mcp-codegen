# Getting Started

This guide will help you install and use OpenAPI MCP Codegen to generate SDKs from OpenAPI specifications.

## Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 17 or higher** - Download from [OpenJDK](https://openjdk.org/) or use your preferred distribution
- **Gradle 8 or higher** - Install via [SDKMAN](https://sdkman.io/), [Homebrew](https://brew.sh/), or from [gradle.org](https://gradle.org/)

### Verification

```bash
java -version   # Should show JDK 17+
gradle --version # Should show Gradle 8+
```

## Installation

### From Source

1. Clone the repository:

```bash
git clone https://github.com/yourusername/openapi-mcp-codegen.git
cd openapi-mcp-codegen
```

2. Build the project:

```bash
./gradlew build
```

3. The generator is now ready to use via Gradle wrapper:

```bash
./gradlew run --args="--input <your-api-spec.yaml> --output <output-dir> --root-package <package-name>"
```

## Basic Usage

### Minimal Example

Generate code from an OpenAPI specification with the minimal required options:

```bash
./gradlew run --args="-i examples/petstore.yaml -o ./generated -r io.swagger.petstore"
```

### Complete Example with Verbose Output

```bash
./gradlew run --args="
  --input examples/petstore.yaml
  --output ./generated
  --root-package io.swagger.petstore
  --verbose
"
```

## CLI Options

| Option | Short | Description | Required | Default |
|--------|-------|-------------|----------|---------|
| `--input` | `-i` | Path to the OpenAPI YAML file | Yes | - |
| `--output` | `-o` | Output directory for generated code | No | `./generated` |
| `--root-package` | `-r` | Root package name (e.g., io.swagger.petstore) | Yes | - |
| `--verbose` | `-v` | Enable verbose output | No | false |

## Using Your Own OpenAPI Spec

1. Place your OpenAPI specification file somewhere accessible:

```bash
# Example structure
my-project/
├── my-api-spec.yaml
└── generated/  # Output directory
```

2. Run the generator:

```bash
./gradlew run --args="-i my-api-spec.yaml -o ./generated -r com.mycompany.api"
```

## Building the Generated Code

Once code generation is complete, navigate to the output directory and build:

```bash
cd generated
./gradlew build
```

This will:
- Compile all generated Kotlin code
- Run tests (if any)
- Create a JAR file in `build/libs/`

### Building Selective JAR (Domain + Client only)

If you only need the domain models and REST client (without MCP tools):

```bash
./gradlew domainClientJar
```

This creates a JAR file containing only the `domain` and `client` packages, excluding the `tool` package.

## Next Steps

- [Generated Code Structure](Generated-Code-Structure) - Understand what gets generated
- [CLI Reference](CLI-Reference) - Detailed command-line options
- [Examples](Examples) - Real-world usage examples

## Troubleshooting

### Java Version Issues

If you encounter a Java version error, ensure you're using JDK 17+:

```bash
# Check current Java version
java -version

# Switch to JDK 17 using SDKMAN
sdk use java 17.0.9-tem
```

### Gradle Build Failures

If the Gradle build fails, try:

```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

### OpenAPI Parsing Errors

Ensure your OpenAPI specification is valid:

- Use an [OpenAPI validator](https://apitools.dev/swagger-parser/online/)
- Check for proper YAML syntax
- Verify all `$ref` references are valid
