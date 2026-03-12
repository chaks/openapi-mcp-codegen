---
sidebar_position: 3
---

# Usage

## Basic Usage

Generate code from an OpenAPI specification:

```bash
java -jar openapi-mcp-codegen.jar \
  --input examples/petstore.yaml \
  --output ./generated \
  --root-package io.swagger.petstore
```

## CLI Options

| Option           | Short | Description                                     | Required                    |
|------------------|-------|-------------------------------------------------|-----------------------------|
| `--input`        | `-i`  | Path to the OpenAPI YAML file                   | Yes                         |
| `--output`       | `-o`  | Output directory for generated code             | No (default: `./generated`) |
| `--root-package` | `-r`  | Root package name (e.g., `io.swagger.petstore`) | Yes                         |
| `--verbose`      | `-v`  | Enable verbose output                           | No                          |
| `--compile`      | `-c`  | Compile the generated code after generation     | No                          |

## Examples

### With Verbose Output

```bash
java -jar openapi-mcp-codegen.jar \
  -i examples/petstore.yaml \
  -o ./generated \
  -r io.swagger.petstore \
  -v
```

### With Auto-Compilation

```bash
java -jar openapi-mcp-codegen.jar \
  -i examples/petstore.yaml \
  -o ./generated \
  -r io.swagger.petstore \
  -c
```

This compiles the generated code after generation using the Gradle wrapper that is included in the output directory.

## Building the Generated Code

### Standard Build

```bash
cd generated
./gradlew build
```

### Build Domain + Client JAR Only

Builds a JAR containing only the `domain` and `client` packages, excluding the `tool` package:

```bash
./gradlew domainClientJar
```

This is useful when you want to distribute the SDK separately from the MCP tool implementation.

## Next Steps

- [Generated Code](./generated-code) - Understand the generated code structure
- [OpenAPI Support](./openapi-support) - See what OpenAPI features are supported
