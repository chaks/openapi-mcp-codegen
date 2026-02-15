# CLI Reference

Complete reference for the OpenAPI MCP Codegen command-line interface.

## Usage

```bash
./gradlew run --args="[OPTIONS]"
```

## Options

### Required Options

| Option | Short | Description | Example |
|--------|-------|-------------|---------|
| `--input` | `-i` | Path to the OpenAPI YAML specification file | `--input examples/petstore.yaml` |
| `--root-package` | `-r` | Root package name for generated code | `--root-package io.swagger.petstore` |

### Optional Options

| Option | Short | Description | Default | Example |
|--------|-------|-------------|---------|---------|
| `--output` | `-o` | Output directory for generated code | `./generated` | `--output ./my-sdk` |
| `--verbose` | `-v` | Enable verbose output for debugging | `false` | `--verbose` |

## Option Details

### `--input` / `-i`

**Description**: Path to the OpenAPI YAML specification file to parse.

**Type**: File path (required)

**Example**:
```bash
./gradlew run --args="-i /path/to/my-api.yaml"
```

**Notes**:
- Must be a valid YAML file
- Supports OpenAPI 3.0 and 3.1 specifications
- Both absolute and relative paths are supported
- The file must exist and be readable

### `--output` / `-o`

**Description**: Directory where generated code will be written.

**Type**: Directory path (optional)

**Default**: `./generated`

**Example**:
```bash
./gradlew run --args="-i api.yaml -o ./my-generated-sdk"
```

**Notes**:
- The directory will be created if it doesn't exist
- Existing files in the directory will be overwritten
- The generator creates the following structure:
  ```
  output/
  ├── build.gradle.kts
  ├── settings.gradle.kts
  └── src/main/kotlin/
      └── <root-package>/
          ├── domain/
          ├── client/
          └── tool/
  ```

### `--root-package` / `-r`

**Description**: Root package name for all generated Kotlin classes.

**Type**: Package name (required)

**Example**:
```bash
./gradlew run --args="-i api.yaml -r com.mycompany.myapi"
```

**Notes**:
- Must be a valid Kotlin package name
- Sub-packages are automatically created:
  - `<root-package>.domain` - Data classes
  - `<root-package>.client` - REST client
  - `<root-package>.tool` - MCP tools
- Reverse domain notation is recommended (e.g., `com.company.project`)

### `--verbose` / `-v`

**Description**: Enable verbose output for debugging and detailed progress information.

**Type**: Flag (optional)

**Default**: `false`

**Example**:
```bash
./gradlew run --args="-i api.yaml -r com.myapi -v"
```

**Notes**:
- Prints detailed parsing progress
- Shows generated file names
- Displays type mapping information
- Useful for debugging OpenAPI specification issues

## Usage Examples

### Basic Generation

Generate code with minimal options:

```bash
./gradlew run --args="-i examples/petstore.yaml -r io.swagger.petstore"
```

### Specify Output Directory

Generate code to a specific directory:

```bash
./gradlew run --args="-i my-api.yaml -o ./generated-sdk -r com.mycompany.api"
```

### Verbose Output

Enable verbose output for debugging:

```bash
./gradlew run --args="-i examples/petstore.yaml -o ./generated -r io.swagger.petstore -v"
```

### Full Example

All options specified:

```bash
./gradlew run --args="
  --input ./specs/my-api-v2.yaml
  --output ./generated/my-api-sdk
  --root-package com.mycompany.myapi.v2
  --verbose
"
```

## Error Messages

### Missing Required Options

```
Error: Missing required option: '--input'
Usage: ./gradlew run --args="[OPTIONS]"
```

### Invalid File Path

```
Error: Input file does not exist: /path/to/nonexistent.yaml
```

### Invalid Package Name

```
Error: Invalid package name: 123invalid
Package names must start with a letter or underscore.
```

### OpenAPI Parse Error

```
Error: Failed to parse OpenAPI specification: ...
```

## Exit Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| 1 | General error (missing options, file not found, etc.) |
| 2 | OpenAPI parse error |
| 3 | Code generation error |

## Best Practices

1. **Use Version Control**: Keep your OpenAPI specs in version control
2. **Consistent Package Names**: Use reverse domain notation
3. **Separate Output**: Use a dedicated `generated/` directory
4. **Verbose for Debugging**: Use `-v` when troubleshooting
5. **Validate First**: Validate your OpenAPI spec before generation

## Related Documentation

- [Getting Started](Getting-Started) - Installation and first steps
- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [OpenAPI Support](OpenAPI-Support) - Supported OpenAPI features