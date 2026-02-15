package io.kritrimabuddhi.codegen.openapi2mcp.parser

import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Parser for OpenAPI 3.0/3.1 YAML specifications.
 *
 * Uses Swagger Parser to parse the YAML file and converts it into
 * the internal ParsedOpenApi model.
 */
@ApplicationScoped
class OpenApiParser {
  private lateinit var currentOpenAPI: OpenAPI

  /**
   * Parse an OpenAPI specification file.
   *
   * @param inputFile Path to the OpenAPI YAML file
   * @return ParsedOpenApi representation of the specification
   */
  fun parse(inputFile: Path): ParsedOpenApi {
    require(inputFile.exists()) {
      "Input file does not exist: ${inputFile.absolutePathString()}"
    }

    val parseOptions = ParseOptions().apply {
      isResolve = true
      isResolveFully = true
    }

    // Parse the specification
    val result = OpenAPIV3Parser().read(inputFile.absolutePathString(), null, parseOptions)
      ?: throw IllegalArgumentException("Failed to parse OpenAPI specification from $inputFile")

    return convertToParsedOpenApi(result)
  }

  private fun convertToParsedOpenApi(openAPI: OpenAPI): ParsedOpenApi {
    this.currentOpenAPI = openAPI
    val info = openAPI.info
    val apiInfo = ApiInfo(
      title = info.title ?: "API",
      version = info.version ?: "1.0.0",
      description = info.description
    )

    val schemas = extractSchemas(openAPI)
    val paths = extractPaths(openAPI)

    return ParsedOpenApi(
      openapiVersion = openAPI.openapi ?: "3.0.0",
      info = apiInfo,
      schemas = schemas,
      paths = paths
    )
  }

  private fun extractSchemas(openAPI: OpenAPI): Map<String, SchemaModel> {
    val schemas = mutableMapOf<String, SchemaModel>()

    openAPI.components?.schemas?.forEach { (name, schema) ->
      schemas[name] = extractSchemaModel(name, schema)
    }

    return schemas
  }

  private fun extractSchemaModel(name: String, schema: Schema<*>): SchemaModel {
    val properties = schema.properties?.mapValues { (propName, propSchema) ->
      extractPropertyInfo(propName, propSchema)
    } ?: emptyMap()

    val required = schema.required?.toSet() ?: emptySet()

    return SchemaModel(
      name = name,
      description = schema.description,
      type = schema.type ?: schema.types?.firstOrNull(),
      properties = properties,
      required = required,
      enum = schema.enum?.map { it.toString() },
      format = schema.format,
      ref = schema.`$ref`
    )
  }

  private fun extractPropertyInfo(name: String, schema: Schema<*>): PropertyInfo {
    val isArray = schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true
    val arrayItemRef = if (isArray) {
      val itemsSchema = schema.items
      itemsSchema?.`$ref`?.let { extractSimpleRef(it) } ?: itemsSchema?.let {
        findRefForSchema(
          it,
          currentOpenAPI
        )
      }
    } else {
      null
    }

    val arrayItemType = if (isArray) schema.items?.type ?: schema.items?.types?.firstOrNull() else null
    val arrayItemFormat = if (isArray) schema.items?.format else null

    return PropertyInfo(
      type = schema.type ?: schema.types?.firstOrNull(),
      format = schema.format,
      description = schema.description,
      isNullable = schema.`default` != null || schema.nullable == true,
      ref = schema.`$ref`?.let { extractSimpleRef(it) } ?: findRefForSchema(schema, currentOpenAPI),
      isArray = isArray,
      arrayItemRef = arrayItemRef,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat,
      enum = schema.enum?.map { it.toString() },
      defaultValue = schema.`default`?.toString()
    )
  }

  private fun extractPaths(openAPI: OpenAPI): List<PathModel> {
    val paths = mutableListOf<PathModel>()

    openAPI.paths?.forEach { (path, pathItem) ->
      pathItem.readOperationsMap().forEach { (method, operation) ->
        paths.add(extractPathModel(path, method.name, operation))
      }
    }

    return paths
  }

  private fun extractPathModel(path: String, method: String, operation: Operation): PathModel {
    val parameters = (operation.parameters ?: emptyList()).map { param ->
      extractParameterInfo(param)
    }

    val requestBody = operation.requestBody?.let { extractRequestBodyInfo(it) }

    val responses = operation.responses?.mapValues { (statusCode, response) ->
      extractResponseInfo(statusCode, response)
    } ?: emptyMap()

    return PathModel(
      path = path,
      method = method,
      operationId = operation.operationId,
      summary = operation.summary,
      description = operation.description,
      parameters = parameters,
      requestBody = requestBody,
      responses = responses,
      tags = operation.tags ?: emptyList()
    )
  }

  private fun extractParameterInfo(parameter: Parameter): ParameterInfo {
    val schema = parameter.schema
    val isArray =
      schema != null && (schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true)
    val arrayItemRef = if (isArray && schema != null) {
      val itemsSchema = schema.items
      itemsSchema?.`$ref`?.let { extractSimpleRef(it) } ?: itemsSchema?.let {
        findRefForSchema(
          it,
          currentOpenAPI
        )
      }
    } else {
      null
    }

    val arrayItemType =
      if (isArray && schema != null) schema.items?.type ?: schema.items?.types?.firstOrNull() else null
    val arrayItemFormat = if (isArray && schema != null) schema.items?.format else null

    return ParameterInfo(
      name = parameter.name,
      `in` = parameter.`in` ?: "query",
      description = parameter.description,
      required = parameter.required ?: false,
      type = schema?.type ?: schema?.types?.firstOrNull(),
      format = schema?.format,
      ref = schema?.`$ref`?.let { extractSimpleRef(it) } ?: schema?.let { findRefForSchema(it, currentOpenAPI) },
      isArray = isArray,
      arrayItemRef = arrayItemRef,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat,
      defaultValue = schema?.`default`?.toString()
    )
  }

  private fun extractRequestBodyInfo(requestBody: RequestBody): RequestBodyInfo {
    val content = requestBody.content
    val mediaType = content?.get("application/json")
      ?: content?.get("application/x-www-form-urlencoded")
      ?: content?.values?.firstOrNull()
      ?: throw IllegalArgumentException("Request body has no supported content type")

    val schema = mediaType.schema

    val isArray =
      schema != null && (schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true)
    val ref = if (isArray && schema != null) {
      val itemsSchema = schema.items
      itemsSchema?.`$ref`?.let { extractSimpleRef(it) } ?: itemsSchema?.let {
        findRefForSchema(
          it,
          currentOpenAPI
        )
      }
    } else {
      schema?.`$ref`?.let { extractSimpleRef(it) } ?: schema?.let { findRefForSchema(it, currentOpenAPI) }
    }

    val arrayItemType =
      if (isArray && schema != null) schema.items?.type ?: schema.items?.types?.firstOrNull() else null
    val arrayItemFormat = if (isArray && schema != null) schema.items?.format else null

    return RequestBodyInfo(
      description = requestBody.description,
      required = requestBody.required ?: false,
      contentType = "application/json",
      format = schema?.format,
      ref = ref,
      isArray = isArray,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat
    )
  }

  private fun extractResponseInfo(statusCode: String, response: ApiResponse): ResponseInfo {
    val mediaType = response.content?.get("application/json")
      ?: response.content?.values?.firstOrNull()

    val schema = mediaType?.schema

    val isArray =
      schema != null && (schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true)
    val ref = if (isArray && schema != null) {
      val itemsSchema = schema.items
      itemsSchema?.`$ref`?.let { extractSimpleRef(it) } ?: itemsSchema?.let {
        findRefForSchema(
          it,
          currentOpenAPI
        )
      }
    } else {
      schema?.`$ref`?.let { extractSimpleRef(it) } ?: schema?.let { findRefForSchema(it, currentOpenAPI) }
    }

    val arrayItemType =
      if (isArray && schema != null) schema.items?.type ?: schema.items?.types?.firstOrNull() else null
    val arrayItemFormat = if (isArray && schema != null) schema.items?.format else null

    return ResponseInfo(
      statusCode = statusCode,
      description = response.description ?: "No description",
      type = schema?.type ?: schema?.types?.firstOrNull(),
      format = schema?.format,
      ref = ref,
      isArray = isArray,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat,
      isNoContent = statusCode == "204"
    )
  }

  private fun findRefForSchema(schema: Schema<*>, openAPI: OpenAPI): String? {
    if (schema.`$ref` != null) return extractSimpleRef(schema.`$ref`)

    // If ref is null, maybe it matches one of the component schemas
    openAPI.components?.schemas?.forEach { (name, componentSchema) ->
      if (schema == componentSchema) return name
    }
    return null
  }

  private fun extractSimpleRef(ref: String): String {
    // Extract simple name from #/components/schemas/Name
    val parts = ref.split("/")
    return parts.lastOrNull() ?: ref
  }
}