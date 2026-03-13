package io.github.chaks.openapi2mcp.parser

import io.github.chaks.openapi2mcp.parser.model.*
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

    val schemas = extractSchemas(openAPI).toMutableMap()
    val paths = extractPaths(openAPI, schemas)

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

    // Extract oneOf, allOf, anyOf references
    val oneOfRefs = schema.oneOf?.mapNotNull { extractRef(it) }
    val allOfRefs = schema.allOf?.mapNotNull { extractRef(it) }
    val anyOfRefs = schema.anyOf?.mapNotNull { extractRef(it) }

    // Extract additionalProperties for map-like objects
    val additionalProperties = schema.additionalProperties?.let {
      if (it is Schema<*>) {
        extractPropertyInfo("additionalProperties", it)
      } else {
        // Boolean or other type - use Any
        PropertyInfo(
          type = "any",
          format = null,
          description = null,
          isNullable = true,
          ref = null,
          isArray = false,
          arrayItemRef = null,
          arrayItemType = null,
          arrayItemFormat = null,
          enum = null,
          defaultValue = null,
          oneOf = null,
          allOf = null,
          anyOf = null
        )
      }
    }

    return SchemaModel(
      name = name,
      description = schema.description,
      type = schema.type ?: schema.types?.firstOrNull(),
      properties = properties,
      required = required,
      enum = schema.enum?.map { it.toString() },
      format = schema.format,
      ref = schema.`$ref`?.let { extractSimpleRef(it) },
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs,
      additionalProperties = additionalProperties
    )
  }

  private fun extractRef(schema: Schema<*>): String? {
    return schema.`$ref`?.let { extractSimpleRef(it) }
      ?: findRefForSchema(schema, currentOpenAPI)
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

    // Extract oneOf, allOf, anyOf references
    val oneOfRefs = schema.oneOf?.mapNotNull { extractRef(it) }
    val allOfRefs = schema.allOf?.mapNotNull { extractRef(it) }
    val anyOfRefs = schema.anyOf?.mapNotNull { extractRef(it) }

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
      defaultValue = schema.`default`?.toString(),
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs
    )
  }

  private fun extractPaths(openAPI: OpenAPI, schemas: MutableMap<String, SchemaModel>): List<PathModel> {
    val paths = mutableListOf<PathModel>()

    openAPI.paths?.forEach { (path, pathItem) ->
      pathItem.readOperationsMap().forEach { (method, operation) ->
        paths.add(extractPathModel(path, method.name, operation, schemas))
      }
    }

    return paths
  }

  private fun extractPathModel(
    path: String,
    method: String,
    operation: Operation,
    schemas: MutableMap<String, SchemaModel>
  ): PathModel {
    val parameters = (operation.parameters ?: emptyList()).map { param ->
      extractParameterInfo(param)
    }

    val requestBody = operation.requestBody?.let { extractRequestBodyInfo(it, method, path, schemas) }

    val responses = operation.responses?.mapValues { (statusCode, response) ->
      extractResponseInfo(statusCode, response, method, path, schemas)
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

  private fun extractRequestBodyInfo(
    requestBody: RequestBody,
    method: String,
    path: String,
    schemas: MutableMap<String, SchemaModel>
  ): RequestBodyInfo {
    val content = requestBody.content
    val mediaType = content?.get("application/json")
      ?: content?.get("application/x-www-form-urlencoded")
      ?: content?.values?.firstOrNull()
      ?: throw IllegalArgumentException("Request body has no supported content type")

    val schema = mediaType.schema

    val isArray =
      schema != null && (schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true)

    // Handle inline object schemas and polymorphic schemas
    var ref: String? = null
    var inlineSchemaRef: String? = null
    var oneOfRefs: List<String>? = null
    var allOfRefs: List<String>? = null
    var anyOfRefs: List<String>? = null

    if (isArray && schema != null) {
      val itemsSchema = schema.items
      val hasItemsRef = itemsSchema?.`$ref` != null

      // Check if array items are inline object schemas
      if (itemsSchema != null && !hasItemsRef && itemsSchema.type == "object" || itemsSchema.types?.contains("object") == true) {
        val schemaName = generateInlineSchemaName(method, path, "Item")
        schemas[schemaName] = extractSchemaModel(schemaName, itemsSchema)
        inlineSchemaRef = schemaName
      }

      ref = itemsSchema?.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
          ?: itemsSchema?.let { findRefForSchema(it, currentOpenAPI) }
    } else {
      val hasRef = schema?.`$ref` != null

      // Check for polymorphic schemas (oneOf, allOf, anyOf)
      val hasOneOf = schema?.oneOf?.isNotEmpty() == true
      val hasAllOf = schema?.allOf?.isNotEmpty() == true
      val hasAnyOf = schema?.anyOf?.isNotEmpty() == true

      if (hasOneOf || hasAllOf || hasAnyOf) {
        // Generate a wrapper schema for polymorphic types
        val schemaName = generateInlineSchemaName(method, path, "Request")
        schemas[schemaName] = extractSchemaModel(schemaName, schema)
        inlineSchemaRef = schemaName

        oneOfRefs = schema?.oneOf?.mapNotNull { extractRef(it) }
        allOfRefs = schema?.allOf?.mapNotNull { extractRef(it) }
        anyOfRefs = schema?.anyOf?.mapNotNull { extractRef(it) }
      } else if (schema != null && !hasRef && (schema.type == "object" || schema.types?.contains("object") == true)) {
        // Check if this is an inline object schema or a resolved component schema reference
        val matchingComponentSchema = findRefForSchema(schema, currentOpenAPI)
        if (matchingComponentSchema != null) {
          // Use the existing component schema reference instead of creating a duplicate
          // This prevents duplicate domain classes like PetPostRequest/PetPutRequest when both reference the same component schema
          ref = matchingComponentSchema
        } else {
          // This is truly an inline schema - generate a new name
          val schemaName = generateInlineSchemaName(method, path, "Request")
          schemas[schemaName] = extractSchemaModel(schemaName, schema)
          inlineSchemaRef = schemaName
        }
      }

      ref = schema?.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
          ?: schema?.let { findRefForSchema(it, currentOpenAPI) }
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
      arrayItemFormat = arrayItemFormat,
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs
    )
  }

  private fun extractResponseInfo(
    statusCode: String,
    response: ApiResponse,
    method: String,
    path: String,
    schemas: MutableMap<String, SchemaModel>
  ): ResponseInfo {
    val mediaType = response.content?.get("application/json")
      ?: response.content?.values?.firstOrNull()

    val schema = mediaType?.schema

    val isArray =
      schema != null && (schema is ArraySchema || schema.type == "array" || schema.types?.contains("array") == true)

    // Handle inline object schemas and polymorphic schemas
    var ref: String? = null
    var inlineSchemaRef: String? = null
    var oneOfRefs: List<String>? = null
    var allOfRefs: List<String>? = null
    var anyOfRefs: List<String>? = null

    if (isArray && schema != null) {
      val itemsSchema = schema.items
      val hasItemsRef = itemsSchema?.`$ref` != null

      // Check if array items are inline object schemas
      if (itemsSchema != null && !hasItemsRef && (itemsSchema.type == "object" || itemsSchema.types?.contains("object") == true)) {
        val schemaName = generateInlineSchemaName(method, path, "Item")
        schemas[schemaName] = extractSchemaModel(schemaName, itemsSchema)
        inlineSchemaRef = schemaName
      }

      ref = itemsSchema?.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
          ?: itemsSchema?.let { findRefForSchema(it, currentOpenAPI) }
    } else {
      val hasRef = schema?.`$ref` != null

      // Check for polymorphic schemas (oneOf, allOf, anyOf)
      val hasOneOf = schema?.oneOf?.isNotEmpty() == true
      val hasAllOf = schema?.allOf?.isNotEmpty() == true
      val hasAnyOf = schema?.anyOf?.isNotEmpty() == true

      if (hasOneOf || hasAllOf || hasAnyOf) {
        // Generate a wrapper schema for polymorphic types
        val schemaName = generateInlineSchemaName(method, path, "Response")
        schemas[schemaName] = extractSchemaModel(schemaName, schema)
        inlineSchemaRef = schemaName

        oneOfRefs = schema?.oneOf?.mapNotNull { extractRef(it) }
        allOfRefs = schema?.allOf?.mapNotNull { extractRef(it) }
        anyOfRefs = schema?.anyOf?.mapNotNull { extractRef(it) }
      } else if (schema != null && !hasRef && (schema.type == "object" || schema.types?.contains("object") == true)) {
        // Check if this is an inline object schema or a resolved component schema reference
        val matchingComponentSchema = findRefForSchema(schema, currentOpenAPI)
        if (matchingComponentSchema != null) {
          // Use the existing component schema reference instead of creating a duplicate
          // This prevents duplicate domain classes when multiple responses reference the same component schema
          ref = matchingComponentSchema
        } else {
          // This is truly an inline schema - generate a new name
          val schemaName = generateInlineSchemaName(method, path, "Response")
          schemas[schemaName] = extractSchemaModel(schemaName, schema)
          inlineSchemaRef = schemaName
        }
      }

      ref = schema?.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
          ?: schema?.let { findRefForSchema(it, currentOpenAPI) }
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
      isNoContent = statusCode == "204",
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs
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

  private fun generateInlineSchemaName(method: String, path: String, suffix: String): String {
    // Generate a meaningful name from the path and method
    // e.g., "POST /users" -> "UsersPostRequest", "GET /users" -> "UsersGetResponse"
    val pathParts = path
      .removePrefix("/")
      .removeSuffix("/")
      .split("/")
      .filterNot { it.startsWith("{") && it.endsWith("}") }
      .map { part ->
        // Convert kebab-case and snake_case to PascalCase
        part
          .split(Regex("[-_]"))
          .joinToString("") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
          }
      }

    val methodPascal = method.lowercase().replaceFirstChar { it.uppercase() }

    return if (pathParts.isNotEmpty()) {
      "${pathParts.lastOrNull() ?: "Inline"}${methodPascal}$suffix"
    } else {
      "${methodPascal}Inline$suffix"
    }
  }
}