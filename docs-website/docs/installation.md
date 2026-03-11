---
sidebar_position: 2
---

# Installation

## Building from Source

### Prerequisites

- **JDK 17+**
- **Gradle 8+**

### Build the Generator

Clone the repository and build the project:

```bash
git clone https://github.com/chaks/openapi-mcp-codegen.git
cd openapi-mcp-codegen
./gradlew build
```

The build will produce a JAR file in the `build/libs/` directory.

### Download the JAR

You can also download the pre-built JAR from the [GitHub Releases](https://github.com/chaks/openapi-mcp-codegen/releases) page.

## Verify Installation

Run the generator with the help flag to verify it's working:

```bash
java -jar build/libs/openapi-mcp-codegen-*.jar --help
```

## Next Steps

Now that you have the generator installed, learn how to use it in the [Usage](./usage) section.
