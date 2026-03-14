package io.github.chaks.openapi2mcp.compiler

import java.nio.file.Path

/**
 * Interface for compiling generated code.
 *
 * Enables DIP compliance by abstracting the build system implementation,
 * allowing for alternative build tools (Gradle, Maven, etc.) and testability.
 */
interface Compiler {

  /**
   * Compiles the generated code in the specified directory.
   *
   * @param outputDir The directory containing the generated project
   * @param verbose Whether to show verbose output
   * @throws RuntimeException if compilation fails
   */
  fun compile(outputDir: Path, verbose: Boolean)

  /**
   * Checks if the build system is available on the current system.
   *
   * @return true if the build system can be executed
   */
  fun isAvailable(): Boolean
}
