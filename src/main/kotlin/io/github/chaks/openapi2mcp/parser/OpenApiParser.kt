package io.github.chaks.openapi2mcp.parser

import io.github.chaks.openapi2mcp.parser.model.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Interface for parsing OpenAPI specifications.
 *
 * Enables DIP compliance and testability by allowing the parser
 * to be mocked or replaced with alternative implementations (e.g., AsyncAPI, RAML).
 */
interface OpenApiParser {

  /**
   * Parse an OpenAPI specification file.
   *
   * @param inputFile Path to the OpenAPI YAML file
   * @return ParsedOpenApi representation of the specification
   * @throws IllegalArgumentException if file doesn't exist or parsing fails
   */
  fun parse(inputFile: Path): ParsedOpenApi

  /**
   * Parse an OpenAPI specification file with normalization.
   *
   * @param inputFile Path to the OpenAPI YAML file
   * @param normalize Whether to apply spec normalization (default: true)
   * @return ParsedOpenApi representation of the specification
   * @throws IllegalArgumentException if file doesn't exist or parsing fails critically
   */
  fun parse(inputFile: Path, normalize: Boolean): ParsedOpenApi
}

/**
 * Default implementation using Swagger Parser library.
 *
 * Parses OpenAPI 3.0/3.1 YAML specifications and converts them
 * to the internal ParsedOpenApi model.
 *
 * DEFENSIVE DESIGN:
 * - Integrates SpecNormalizer for hostile input handling
 * - Applies safe defaults for malformed specs
 * - Logs warnings for non-compliant patterns
 * - Continues processing when possible instead of failing
 */
@ApplicationScoped
class SwaggerOpenApiParser : OpenApiParser {

  companion object {
    private val LOG = LoggerFactory.getLogger(SwaggerOpenApiParser::class.java)
  }

  private lateinit var currentOpenAPI: OpenAPI

  @Inject
  private lateinit var schemaExtractor: SchemaExtractor

  @Inject
  private lateinit var specNormalizer: SpecNormalizer

  override fun parse(inputFile: Path): ParsedOpenApi {
    return parse(inputFile, normalize = true)
  }

  override fun parse(inputFile: Path, normalize: Boolean): ParsedOpenApi {
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

    // DEFENSIVE: Normalize the spec before processing
    if (normalize) {
      val normalizationResult = specNormalizer.normalize(result)

      if (!normalizationResult.isValid) {
        // DEFENSIVE: Log errors but continue with safe defaults
        LOG.error("Spec has ${normalizationResult.errors.size} critical issues - attempting to continue with safe defaults")
      } else if (normalizationResult.shouldProceedWithWarnings()) {
        LOG.info("Spec normalized with ${normalizationResult.warnings.size} warnings - proceeding")
      }
    }

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

    val schemas = schemaExtractor.extractSchemas(openAPI).toMutableMap()
    val paths = extractPaths(openAPI, schemas)

    return ParsedOpenApi(
      openapiVersion = openAPI.openapi ?: "3.0.0",
      info = apiInfo,
      schemas = schemas,
      paths = paths
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
        schemaExtractor.findRefForSchema(
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
      ref = schema?.`$ref`?.let { extractSimpleRef(it) } ?: schema?.let {
        schemaExtractor.findRefForSchema(
          it,
          currentOpenAPI
        )
      },
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

    // DEFENSIVE: Handle null or empty content with safe default
    val mediaType = content?.get("application/json")
      ?: content?.get("application/x-www-form-urlencoded")
      ?: content?.get("multipart/form-data")
      ?: content?.values?.firstOrNull()

    // DEFENSIVE: Instead of throwing, apply safe default
    if (mediaType == null) {
      LOG.warn("Request body for $method $path has no supported content type - using safe default")
      return RequestBodyInfo(
        description = requestBody.description ?: "Request body",
        required = requestBody.required ?: false,
        contentType = "application/json",
        ref = null,
        isArray = false,
        oneOf = null,
        allOf = null,
        anyOf = null
      )
    }

    val schema = mediaType.schema

    // DEFENSIVE: Handle null schema
    if (schema == null) {
      LOG.warn("Request body for $method $path has null schema - using Any type")
      return RequestBodyInfo(
        description = requestBody.description ?: "Request body",
        required = requestBody.required ?: false,
        contentType = "application/json",
        ref = null,
        isArray = false,
        oneOf = null,
        allOf = null,
        anyOf = null
      )
    }

    val isArray = safeDetectArray(schema)

    // Handle inline object schemas and polymorphic schemas
    var ref: String? = null
    var inlineSchemaRef: String? = null
    var oneOfRefs: List<String>? = null
    var allOfRefs: List<String>? = null
    var anyOfRefs: List<String>? = null

    if (isArray) {
      val itemsSchema = schema.items
      val hasItemsRef = itemsSchema?.`$ref` != null

      // DEFENSIVE: Handle null items schema
      if (itemsSchema == null) {
        LOG.warn("Array request body for $method $path has no items schema - using Any")
        ref = null
      } else {
        // Check if array items are inline object schemas - DEFENSIVE: fix operator precedence
        if (!hasItemsRef && (itemsSchema.type == "object" || itemsSchema.types?.contains("object") == true)) {
          val schemaName = generateInlineSchemaName(method, path, "Item")
          schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, itemsSchema)
          inlineSchemaRef = schemaName
        }

        ref = itemsSchema?.`$ref`?.let { extractSimpleRef(it) }
          ?: inlineSchemaRef
          ?: itemsSchema.let { schemaExtractor.findRefForSchema(it, currentOpenAPI) }
      }
    } else {
      val hasRef = schema.`$ref` != null

      // Check for polymorphic schemas (oneOf, allOf, anyOf)
      val hasOneOf = schema.oneOf?.isNotEmpty() == true
      val hasAllOf = schema.allOf?.isNotEmpty() == true
      val hasAnyOf = schema.anyOf?.isNotEmpty() == true

      if (hasOneOf || hasAllOf || hasAnyOf) {
        // Generate a wrapper schema for polymorphic types
        val schemaName = generateInlineSchemaName(method, path, "Request")
        schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, schema)
        inlineSchemaRef = schemaName

        oneOfRefs = schema.oneOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
        allOfRefs = schema.allOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
        anyOfRefs = schema.anyOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
      } else if (!hasRef && (schema.type == "object" || schema.types?.contains("object") == true)) {
        // Check if this is an inline object schema or a resolved component schema reference
        val matchingComponentSchema = schemaExtractor.findRefForSchema(schema, currentOpenAPI)
        if (matchingComponentSchema != null) {
          // Use the existing component schema reference instead of creating a duplicate
          ref = matchingComponentSchema
        } else {
          // This is truly an inline schema - generate a new name
          val schemaName = generateInlineSchemaName(method, path, "Request")
          schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, schema)
          inlineSchemaRef = schemaName
        }
      }

      ref = schema.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
        ?: schemaExtractor.findRefForSchema(schema, currentOpenAPI)
    }

    val arrayItemType = if (isArray) schema.items?.let { safeExtractType(it) } else null
    val arrayItemFormat = if (isArray) schema.items?.format else null

    return RequestBodyInfo(
      description = requestBody.description,
      required = requestBody.required ?: false,
      contentType = "application/json",
      format = schema.format,
      ref = ref,
      isArray = isArray,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat,
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs
    )
  }

  /**
   * DEFENSIVE: Safely detects if a schema is an array type.
   */
  private fun safeDetectArray(schema: io.swagger.v3.oas.models.media.Schema<*>): Boolean {
    return schema is ArraySchema ||
      schema.type == "array" ||
      schema.types?.any { it == "array" } == true ||
      schema.items != null
  }

  /**
   * DEFENSIVE: Safely extracts type from schema with null handling.
   */
  private fun safeExtractType(schema: io.swagger.v3.oas.models.media.Schema<*>): String? {
    return schema.type
      ?: schema.types?.firstOrNull { !it.isNullOrBlank() }
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

    // DEFENSIVE: Handle null schema gracefully
    if (schema == null) {
      LOG.debug("Response $statusCode for $method $path has no schema - using Any type")
      return ResponseInfo(
        statusCode = statusCode,
        description = response.description ?: "Response $statusCode",
        ref = null,
        isArray = false,
        isNoContent = statusCode == "204",
        oneOf = null,
        allOf = null,
        anyOf = null
      )
    }

    val isArray = safeDetectArray(schema)

    // Handle inline object schemas and polymorphic schemas
    var ref: String? = null
    var inlineSchemaRef: String? = null
    var oneOfRefs: List<String>? = null
    var allOfRefs: List<String>? = null
    var anyOfRefs: List<String>? = null

    if (isArray) {
      val itemsSchema = schema.items

      // DEFENSIVE: Handle null items
      if (itemsSchema == null) {
        LOG.warn("Array response $statusCode for $method $path has no items schema - using Any")
        ref = null
      } else {
        val hasItemsRef = itemsSchema.`$ref` != null

        // Check if array items are inline object schemas
        if (!hasItemsRef && (itemsSchema.type == "object" || itemsSchema.types?.contains("object") == true)) {
          val schemaName = generateInlineSchemaName(method, path, "Item")
          schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, itemsSchema)
          inlineSchemaRef = schemaName
        }

        ref = itemsSchema.`$ref`?.let { extractSimpleRef(it) }
          ?: inlineSchemaRef
          ?: schemaExtractor.findRefForSchema(itemsSchema, currentOpenAPI)
      }
    } else {
      val hasRef = schema.`$ref` != null

      // Check for polymorphic schemas (oneOf, allOf, anyOf)
      val hasOneOf = schema.oneOf?.isNotEmpty() == true
      val hasAllOf = schema.allOf?.isNotEmpty() == true
      val hasAnyOf = schema.anyOf?.isNotEmpty() == true

      if (hasOneOf || hasAllOf || hasAnyOf) {
        // Generate a wrapper schema for polymorphic types
        val schemaName = generateInlineSchemaName(method, path, "Response")
        schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, schema)
        inlineSchemaRef = schemaName

        oneOfRefs = schema.oneOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
        allOfRefs = schema.allOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
        anyOfRefs = schema.anyOf?.mapNotNull { schemaExtractor.extractRef(it, currentOpenAPI) }
      } else if (!hasRef && (schema.type == "object" || schema.types?.contains("object") == true)) {
        // Check if this is an inline object schema or a resolved component schema reference
        val matchingComponentSchema = schemaExtractor.findRefForSchema(schema, currentOpenAPI)
        if (matchingComponentSchema != null) {
          ref = matchingComponentSchema
        } else {
          val schemaName = generateInlineSchemaName(method, path, "Response")
          schemas[schemaName] = schemaExtractor.extractSchemaModel(schemaName, schema)
          inlineSchemaRef = schemaName
        }
      }

      ref = schema.`$ref`?.let { extractSimpleRef(it) }
        ?: inlineSchemaRef
        ?: schemaExtractor.findRefForSchema(schema, currentOpenAPI)
    }

    val arrayItemType = if (isArray) schema.items?.let { safeExtractType(it) } else null
    val arrayItemFormat = if (isArray) schema.items?.format else null

    return ResponseInfo(
      statusCode = statusCode,
      description = response.description ?: "Response $statusCode",
      type = schema.type ?: schema.types?.firstOrNull { !it.isNullOrBlank() },
      format = schema.format,
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
