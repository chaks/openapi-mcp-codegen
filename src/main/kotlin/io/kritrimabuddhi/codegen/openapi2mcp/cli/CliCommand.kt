package io.kritrimabuddhi.codegen.openapi2mcp.cli

import io.kritrimabuddhi.codegen.openapi2mcp.generator.CodeGenerator
import io.kritrimabuddhi.codegen.openapi2mcp.parser.OpenApiParser
import io.quarkus.picocli.runtime.annotations.TopCommand
import jakarta.inject.Inject
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Main CLI command for the OpenAPI to MCP generator.
 *
 * Usage:
 * ```
 * ./gradlew run --args="-i openapi.yaml -o ./generated -r com.petstore"
 * ```
 *
 * Or with long options:
 * ```
 * ./gradlew run --args="--input openapi.yaml --output ./generated --root-package com.petstore"
 * ```
 */
@TopCommand
@Command(
  name = "openapi-to-mcp",
  mixinStandardHelpOptions = true,
  description = [
    "Parse an OpenAPI 3.0/3.1 YAML specification and generate a Quarkus-based SDK with MCP Toolset.",
    "",
    "Generated packages:",
    "  {root}.domain  - Data classes with Jackson/JSON-B annotations",
    "  {root}.client - REST client interfaces using MicroProfile Rest Client",
    "  {root}.tool   - MCP tool wrappers using LangChain4j"
  ]
)
class CliCommand : Runnable {

  @Inject
  lateinit var parser: OpenApiParser

  @Inject
  lateinit var generator: CodeGenerator

  @Option(
    names = ["-i", "--input"],
    description = ["Path to the input OpenAPI YAML specification file"],
    required = true
  )
  private lateinit var inputPath: String

  @Option(
    names = ["-o", "--output"],
    description = ["Directory where generated code will be written"],
    defaultValue = "./generated"
  )
  private var outputPath: String = "./generated"

  @Option(
    names = ["-r", "--root-package"],
    description = ["Root package name for generated code (e.g., com.petstore)"],
    required = true
  )
  private lateinit var rootPackage: String

  @Option(
    names = ["-v", "--verbose"],
    description = ["Enable verbose output"]
  )
  private var verbose: Boolean = false

  @Option(
    names = ["-c", "--compile"],
    description = ["Compile the generated code after generation"]
  )
  private var compile: Boolean = false

  override fun run() {
    try {
      val input = Paths.get(inputPath).toAbsolutePath().normalize()
      val output = Paths.get(outputPath).toAbsolutePath().normalize()

      // Validate input file exists
      if (!Files.exists(input)) {
        System.err.println("Error: Input file does not exist: $input")
        CommandLine(this).usage(System.err)
        exitProcess(1)
      }

      if (!Files.isRegularFile(input)) {
        System.err.println("Error: Input path is not a file: $input")
        exitProcess(1)
      }

      // Create output directory if needed
      Files.createDirectories(output)

      if (verbose) {
        println("Input file: $input")
        println("Output directory: $output")
        println("Root package: $rootPackage")
        println()
      }

      val options = CliOptions(input, output, rootPackage)

      // Parse OpenAPI specification
      if (verbose) println("Parsing OpenAPI specification...")
      val parsedApi = parser.parse(input)

      if (verbose) {
        println("Parsed OpenAPI ${parsedApi.openapiVersion}")
        println("  - Schemas: ${parsedApi.schemas.size}")
        println("  - Paths: ${parsedApi.paths.size}")
        println()
      }

      // Generate code
      if (verbose) println("Generating code...")
      generator.generate(parsedApi, options)

      println("Code generation complete!")
      println("  Output directory: $output")
      println("  Root package: $rootPackage")
      println()
      println("Generated structure:")
      println("  - ${options.domainPackage.replace('.', '/')}  (domain layer)")
      println("  - ${options.clientPackage.replace('.', '/')}  (client layer)")
      println("  - ${options.toolPackage.replace('.', '/')}   (tool layer)")
      println()

      // Compile generated code if requested
      if (compile) {
        try {
          generator.compile(options.output, verbose)
          println()
          println("Compilation complete!")
          println("  JAR location: ${options.output.resolve("build/libs")}")
        } catch (e: Exception) {
          println()
          System.err.println("Warning: Compilation failed: ${e.message}")
          if (verbose) {
            e.printStackTrace(System.err)
          }
          // Continue execution - don't fail the whole process
        }
      }

    } catch (e: Exception) {
      System.err.println("Error: ${e.message}")
      if (verbose) {
        System.err.println()
        e.printStackTrace(System.err)
      }
      exitProcess(1)
    }
  }
}