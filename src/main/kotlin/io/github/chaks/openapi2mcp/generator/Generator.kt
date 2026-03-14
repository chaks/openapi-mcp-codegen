package io.github.chaks.openapi2mcp.generator

import io.github.chaks.openapi2mcp.cli.CliOptions

/**
 * Common interface for all code generators.
 *
 * Enables dependency inversion and testability by allowing generators
 * to be mocked or replaced with alternative implementations.
 */
interface Generator {

  /**
   * Generate code artifacts from the input.
   *
   * @param options CLI options containing output directory and package info
   */
  fun generate(options: CliOptions)
}
