# OpenAPI MCP Codegen

A powerful CLI tool that parses OpenAPI 3.0/3.1 YAML specifications and generates a structured Quarkus-based SDK with MCP (Model Context Protocol) Toolset integration.

## Quick Start

```bash
# Clone the repository
git clone https://github.com/yourusername/openapi-mcp-codegen.git
cd openapi-mcp-codegen

# Build the generator
./gradlew build

# Generate code from an OpenAPI spec
./gradlew run --args="--input examples/petstore.yaml --output ./generated --root-package io.swagger.petstore"

# Build the generated code
cd generated
./gradlew build
```

## Overview

This tool automates the generation of three distinct layers:

### 1. Domain Layer
Type-safe Kotlin data classes with Jackson/JSON-B annotations for serialization/deserialization.

### 2. Client Layer
REST client interfaces using MicroProfile Rest Client, fully integrated with Quarkus.

### 3. MCP Tool Layer
LangChain4j `@Tool` annotated wrappers that enable AI/LLM agents to interact with the API.

## Key Features

- **OpenAPI Support**: Parses OpenAPI 3.0 and 3.1 specifications
- **Type Safety**: Generates type-safe Kotlin code using KotlinPoet
- **Quarkus Ready**: Creates Quarkus-compatible REST clients
- **AI Integration**: Built-in LangChain4j MCP tools for AI agent integration
- **Selective Packaging**: Option to build JARs with domain+client only (excludes tools)
- **Comprehensive KDoc**: Full documentation for all generated code

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

## Use Cases

1. **API SDK Generation**: Quickly generate type-safe SDKs for REST APIs
2. **AI Agent Integration**: Create tools that allow LLMs to interact with your APIs
3. **Microservices Development**: Generate client code for microservice communication
4. **API Testing**: Build clients to test and validate API specifications

## Requirements

- JDK 17+
- Gradle 8+

## Documentation

- [Getting Started](Getting-Started) - Installation and first steps
- [CLI Reference](CLI-Reference) - Complete command-line options
- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [Type Mapping](Type-Mapping) - OpenAPI to Kotlin type conversion
- [OpenAPI Support](OpenAPI-Support) - Supported OpenAPI features
- [Examples](Examples) - Real-world usage examples
- [Architecture](Architecture) - Deep dive into the codebase
- [Development Guide](Development-Guide) - Contributing to the project

## License

This project is provided as-is for educational and commercial use.