# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-11

### Added
- Initial release of OpenAPI MCP Codegen
- OpenAPI 3.0 and 3.1 specification parser
- Domain layer generator - creates data classes with Jackson/JSON-B annotations
- Client layer generator - creates REST client interfaces using MicroProfile Rest Client
- MCP Tool layer generator - creates Quarkus MCP server `@Tool` and `@ToolArg` annotated wrappers
- Type-safe Kotlin code generation using KotlinPoet
- Gradle build configuration generator for output
- Gradle wrapper generation for easy compilation of generated code
- Auto-compilation option (`--compile`) to compile generated code immediately
- Selective JAR packaging task (`domainClientJar`) - packages only domain and client sources
- Verbose output mode (`--verbose`) for debugging
- Comprehensive type mapping for OpenAPI types (string, integer, number, boolean, array, object)
- Support for path, query, header, and cookie parameters
- Request body handling with schema references
- Response handling with schema references
- Enum value support
- Required/optional field handling
- Arrays and nested object support
- Wiki documentation for getting started, CLI reference, type mapping, OpenAPI support, and examples
- Documentation website with Docusaurus
- Apache 2.0 license

### Changed
- Package renamed from `io.kritrimabuddhi.codegen.openapi2mcp` to `io.github.chaks.openapi2mcp`
- Improved inline schema handling in code generation
- Renamed internal generator classes for clarity

### Removed
- Build config generation (simplified output structure)

### CI/CD
- Added GitHub Actions build workflow with Gradle and JDK 17

### Documentation
- Updated build instructions in README
- Added website with comprehensive documentation
- Added wiki pages for architecture and development guide

[1.0.0]: https://github.com/chaks/openapi-mcp-codegen/releases/tag/v1.0.0
