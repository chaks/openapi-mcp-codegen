---
sidebar_position: 1
---

# Introduction

OpenAPI MCP Codegen is a CLI tool that parses OpenAPI 3.0/3.1 YAML specifications and generates a structured Quarkus-based SDK with MCP (Model Context Protocol) Toolset integration for AI/LLM agent usage.

## Overview

This tool automates the generation of:

- **Domain Layer**: Data classes with Jackson/JSON-B annotations
- **Client Layer**: REST client interfaces using MicroProfile Rest Client
- **MCP Tool Layer**: Quarkus MCP server `@Tool` and `@ToolArg` annotated wrappers for AI/LLM integration

## Why OpenAPI MCP Codegen?

### Type-Safe Generation
Generate type-safe Kotlin code using KotlinPoet from your OpenAPI specifications. No more writing boilerplate code by hand.

### MCP Integration
Automatically generate Quarkus MCP tools that can be directly consumed by AI agents through the Model Context Protocol.

### Ready to Build
Each generated project includes a complete Gradle setup with all necessary dependencies, ready to compile and deploy.

## Requirements

- **JDK**: 17 or higher
- **Gradle**: 8 or higher
- **Input**: OpenAPI 3.0 or 3.1 YAML specification

## What Gets Generated?

For a root package of `io.swagger.petstore`, the generator creates:

```
generated/
├── build.gradle.kts              # Gradle build configuration
├── settings.gradle.kts           # Gradle settings file
├── gradlew                       # Gradle wrapper script (Unix)
├── gradlew.bat                   # Gradle wrapper script (Windows)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
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
    └── application.properties     # Quarkus configuration
```

## Next Steps

- [Installation](./installation) - Learn how to install the tool
- [Usage](./usage) - See how to generate code from OpenAPI specs
- [Generated Code](./generated-code) - Understand the generated code structure
- [OpenAPI Support](./openapi-support) - See what OpenAPI features are supported
