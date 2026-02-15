package io.kritrimabuddhi.codegen.openapi2mcp.cli

import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.file.Path

/**
 * Data class representing CLI options for the OpenAPI to MCP generator.
 *
 * @property input The path to the input OpenAPI YAML specification file
 * @property output The directory where generated code will be written
 * @property rootPackage The root package name for generated code (e.g., com.example.weather)
 */
data class CliOptions(
  @JsonProperty("input")
  val input: Path,

  @JsonProperty("output")
  val output: Path,

  @JsonProperty("rootPackage")
  val rootPackage: String
) {
  init {
    require(rootPackage.isNotBlank()) {
      "Root package name cannot be blank"
    }
    require(rootPackage.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$"))) {
      "Root package must be a valid package name (e.g., com.example.weather)"
    }
  }

  /**
   * Returns the domain package name.
   */
  val domainPackage: String
    get() = "$rootPackage.domain"

  /**
   * Returns the client package name.
   */
  val clientPackage: String
    get() = "$rootPackage.client"

  /**
   * Returns the tool package name.
   */
  val toolPackage: String
    get() = "$rootPackage.tool"

  /**
   * Returns the client interface name derived from the root package.
   * Example: com.example.weather -> WeatherClient
   */
  val clientName: String
    get() {
      val parts = rootPackage.split(".")
      val simpleName = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: "Api"
      return "${simpleName.replaceFirstChar { it.uppercase() }}Client"
    }

  /**
   * Returns the tool class name derived from the root package.
   * Example: com.example.weather -> WeatherTools
   */
  val toolName: String
    get() {
      val parts = rootPackage.split(".")
      val simpleName = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: "Api"
      return "${simpleName.replaceFirstChar { it.uppercase() }}Tools"
    }

  /**
   * Returns the config key for the REST client.
   * Example: com.example.weather -> weather-api
   */
  val configKey: String
    get() {
      val parts = rootPackage.split(".")
      val simpleName = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: "api"
      return "${simpleName.lowercase()}-api"
    }
}