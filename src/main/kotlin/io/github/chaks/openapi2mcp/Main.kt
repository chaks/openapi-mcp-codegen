package io.github.chaks.openapi2mcp

import io.github.chaks.openapi2mcp.cli.CliCommand
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import jakarta.inject.Inject
import picocli.CommandLine

/**
 * Main entry point for the OpenAPI to MCP generator.
 *
 * This application uses Quarkus with Picocli to provide a command-line interface
 * for generating Quarkus-based SDK code from OpenAPI specifications.
 */
@QuarkusMain
class Main : QuarkusApplication {

  @Inject
  lateinit var factory: CommandLine.IFactory

  @Inject
  @io.quarkus.picocli.runtime.annotations.TopCommand
  lateinit var cliCommand: CliCommand

  override fun run(vararg args: String): Int {
    return CommandLine(cliCommand, factory).execute(*args)
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      Quarkus.run(Main::class.java, *args)
    }
  }
}