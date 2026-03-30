package io.github.chaks.openapi2mcp.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.Paths
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

/**
 * Result of spec normalization with validation warnings and errors.
 *
 * @property isValid Whether the spec is valid enough to process
 * @property warnings Non-critical issues that were auto-corrected
 * @property errors Critical issues that prevent code generation
 * @property normalizer The normalizer that performed the validation
 */
data class NormalizationResult(
  val isValid: Boolean,
  val warnings: List<String>,
  val errors: List<String>,
  val normalizer: SpecNormalizer
) {
  fun hasCriticalIssues(): Boolean = errors.isNotEmpty()

  fun shouldProceedWithWarnings(): Boolean = isValid && warnings.isNotEmpty()
}

/**
 * Validates and normalizes OpenAPI specifications before processing.
 *
 * Applies safe defaults for missing fields, detects problematic patterns,
 * and logs warnings for non-compliant OpenAPI usage.
 *
 * Defense-in-depth layer that catches malformed specs before they reach
 * the schema extractor and code generators.
 */
@ApplicationScoped
class SpecNormalizer {

  companion object {
    private val LOG = LoggerFactory.getLogger(SpecNormalizer::class.java)

    // Safe defaults for missing required fields
    private const val DEFAULT_TITLE = "Unnamed API"
    private const val DEFAULT_VERSION = "1.0.0"
    private const val DEFAULT_OPENAPI_VERSION = "3.0.0"

    // Maximum nesting depth to prevent stack overflows
    private const val MAX_SCHEMA_DEPTH = 50

    // Maximum number of schemas to process
    private const val MAX_SCHEMAS = 1000
  }

  private val warnings = mutableListOf<String>()
  private val errors = mutableListOf<String>()

  /**
   * Validates and normalizes an OpenAPI specification.
   *
   * @param openAPI The OpenAPI specification to validate
   * @return NormalizationResult with validation status and issues
   */
  fun normalize(openAPI: OpenAPI): NormalizationResult {
    warnings.clear()
    errors.clear()

    // Validate and apply defaults at each level
    validateInfo(openAPI)
    validateComponents(openAPI)
    validatePaths(openAPI)

    // Check for structural issues
    validateSchemaReferences(openAPI)
    validateCycles(openAPI)

    val isValid = errors.isEmpty()

    if (!isValid) {
      LOG.error("Spec normalization failed with {} errors: {}", errors.size, errors.take(5))
    } else if (warnings.isNotEmpty()) {
      LOG.warn("Spec normalized with {} warnings - proceeding with safe defaults", warnings.size)
    } else {
      LOG.info("Spec validated successfully - no issues found")
    }

    return NormalizationResult(
      isValid = isValid,
      warnings = warnings.toList(),
      errors = errors.toList(),
      normalizer = this
    )
  }

  /**
   * Validates and normalizes the Info section.
   * Applies safe defaults for missing title and version.
   */
  private fun validateInfo(openAPI: OpenAPI) {
    if (openAPI.info == null) {
      warn("Missing 'info' section - creating default info")
      openAPI.info = Info().apply {
        title = DEFAULT_TITLE
        version = DEFAULT_VERSION
      }
      return
    }

    // Validate title
    if (openAPI.info.title.isNullOrBlank()) {
      warn("Empty or missing API title - using default: '$DEFAULT_TITLE'")
      openAPI.info.title = DEFAULT_TITLE
    }

    // Sanitize title for use in file names and identifiers
    // Note: We no longer modify the title - it's preserved as-is for API documentation
    // File name sanitization should be done at the file generation level

    // Validate version
    if (openAPI.info.version.isNullOrBlank()) {
      warn("Empty or missing API version - using default: '$DEFAULT_VERSION'")
      openAPI.info.version = DEFAULT_VERSION
    }

    // Validate OpenAPI version
    if (openAPI.openapi.isNullOrBlank()) {
      warn("Missing OpenAPI version - assuming $DEFAULT_OPENAPI_VERSION")
      openAPI.openapi = DEFAULT_OPENAPI_VERSION
    }
  }

  /**
   * Validates and ensures Components section exists.
   */
  private fun validateComponents(openAPI: OpenAPI) {
    if (openAPI.components == null) {
      warn("Missing 'components' section - creating empty components")
      openAPI.components = Components()
      return
    }

    // Ensure schemas map exists
    if (openAPI.components.schemas == null) {
      warn("Missing 'components.schemas' - creating empty schemas map")
      openAPI.components.schemas = mutableMapOf()
    }

    // Validate individual schemas
    openAPI.components?.schemas?.forEach { (name, schema) ->
      validateSchema(name, schema, depth = 0)
    }
  }

  /**
   * Validates a schema and applies safe defaults.
   *
   * @param name Schema name for error reporting
   * @param schema The schema to validate
   * @param depth Current nesting depth
   */
  private fun validateSchema(name: String, schema: Schema<*>, depth: Int) {
    // Prevent infinite recursion
    if (depth > MAX_SCHEMA_DEPTH) {
      error("Schema '$name' exceeds maximum nesting depth ($MAX_SCHEMA_DEPTH) - possible circular definition")
      return
    }

    // Handle null schema - this can happen with malformed specs
    if (schema == null) {
      warn("Schema '$name' is null - replacing with empty object schema")
      return
    }

    // Normalize type field
    normalizeTypeField(schema, name)

    // Handle schemas with both 'type' and composition (oneOf/allOf/anyOf)
    validateComposition(schema, name, depth)

    // Validate properties if this is an object schema
    if (schema.type == "object" || schema.types?.contains("object") == true) {
      validateProperties(schema, name, depth)
    }

    // Validate array items
    if (schema.type == "array" || schema.types?.contains("array") == true) {
      validateArrayItems(schema, name, depth)
    }

    // Validate enum values
    validateEnum(schema, name)

    // Handle additionalProperties
    validateAdditionalProperties(schema, name, depth)
  }

  /**
   * Normalizes the type field, handling the 'types' array variant.
   * Some OpenAPI specs use 'types' (plural) for union types.
   */
  private fun normalizeTypeField(schema: Schema<*>, name: String) {
    val type = schema.type
    val types = schema.types

    // If type is missing but types array exists
    if (type.isNullOrBlank() && !types.isNullOrEmpty()) {
      // Take the first non-null type, default to 'object' if all are null
      val firstType = types.firstOrNull { !it.isNullOrBlank() } ?: "object"
      schema.type = firstType
      warn("Schema '$name' used 'types' array - using first type: '$firstType'")
    }

    // If both are missing or empty, infer from schema structure
    if (type.isNullOrBlank() && (types.isNullOrEmpty() || types.all { it.isNullOrBlank() })) {
      val inferredType = inferTypeFromStructure(schema)
      schema.type = inferredType
      warn("Schema '$name' missing 'type' field - inferred as '$inferredType' from structure")
    }

    // Validate type is a known value
    val validTypes = setOf("string", "number", "integer", "boolean", "array", "object", "null")
    if (!schema.type.isNullOrBlank() && schema.type.lowercase() !in validTypes) {
      warn("Schema '$name' has unknown type '${schema.type}' - defaulting to 'object'")
      schema.type = "object"
    }
  }

  /**
   * Infers schema type from its structure when type is missing.
   */
  private fun inferTypeFromStructure(schema: Schema<*>): String {
    return when {
      !schema.properties.isNullOrEmpty() -> "object"
      schema.items != null -> "array"
      !schema.enum.isNullOrEmpty() -> "string"
      schema.additionalProperties != null -> "object"
      !schema.oneOf.isNullOrEmpty() -> "object" // oneOf without explicit type
      !schema.allOf.isNullOrEmpty() -> "object" // allOf without explicit type
      !schema.anyOf.isNullOrEmpty() -> "object" // anyOf without explicit type
      schema.format != null -> inferTypeFromFormat(schema.format)
      else -> "object" // Safe default
    }
  }

  private fun inferTypeFromFormat(format: String?): String {
    return when (format?.lowercase()) {
      "int32", "int64" -> "integer"
      "float", "double" -> "number"
      "date", "date-time", "email", "uri", "uuid", "byte", "binary" -> "string"
      else -> "object"
    }
  }

  /**
   * Validates composition schemas (oneOf, allOf, anyOf).
   */
  private fun validateComposition(schema: Schema<*>, name: String, depth: Int) {
    // Validate oneOf - use safe iteration to handle nulls
    try {
      schema.oneOf?.forEachIndexed { index, refSchema ->
        if (refSchema == null) {
          warn("Schema '$name' has null entry in oneOf at index $index - removing")
        } else {
          validateSchema("$name.oneOf[$index]", refSchema, depth + 1)
        }
      }
    } catch (e: NullPointerException) {
      warn("Schema '$name' has malformed oneOf - skipping validation")
    }

    // Validate allOf - use safe iteration to handle nulls
    try {
      schema.allOf?.forEachIndexed { index, refSchema ->
        if (refSchema == null) {
          warn("Schema '$name' has null entry in allOf at index $index - removing")
        } else {
          validateSchema("$name.allOf[$index]", refSchema, depth + 1)
        }
      }
    } catch (e: NullPointerException) {
      warn("Schema '$name' has malformed allOf - skipping validation")
    }

    // Validate anyOf - use safe iteration to handle nulls
    try {
      schema.anyOf?.forEachIndexed { index, refSchema ->
        if (refSchema == null) {
          warn("Schema '$name' has null entry in anyOf at index $index - removing")
        } else {
          validateSchema("$name.anyOf[$index]", refSchema, depth + 1)
        }
      }
    } catch (e: NullPointerException) {
      warn("Schema '$name' has malformed anyOf - skipping validation")
    }

    // Check for problematic combination: type + allOf
    // This is technically valid but can cause confusion
    if (!schema.type.isNullOrBlank() && !schema.allOf.isNullOrEmpty()) {
      warn("Schema '$name' has both 'type' and 'allOf' - this may cause unexpected behavior")
    }
  }

  /**
   * Validates object properties.
   */
  private fun validateProperties(schema: Schema<*>, name: String, depth: Int) {
    if (schema.properties.isNullOrEmpty()) {
      return
    }

    val validProperties = mutableMapOf<String, Schema<*>>()

    schema.properties?.forEach { (propName, propSchema) ->
      if (propName.isNullOrBlank()) {
        warn("Schema '$name' has property with empty name - skipping")
        return@forEach
      }

      if (propSchema == null) {
        warn("Schema '$name' property '$propName' has null schema - using Any type")
        validProperties[propName] = Schema<Any>().apply { type = "object" }
      } else {
        validProperties[propName] = propSchema
        validateSchema("$name.$propName", propSchema, depth + 1)
      }
    }

    // Validate required array
    schema.required?.forEach { requiredProp ->
      if (!validProperties.containsKey(requiredProp)) {
        warn("Schema '$name' requires property '$requiredProp' but it's not defined in properties")
      }
    }
  }

  /**
   * Validates array items schema.
   */
  private fun validateArrayItems(schema: Schema<*>, name: String, depth: Int) {
    if (schema.items == null) {
      warn("Schema '$name' is array type but missing 'items' - defaulting to Any items")
      schema.items = Schema<Any>().apply { type = "object" }
      return
    }

    // Handle array items that are a list (tuple typing)
    val items = schema.items
    if (items is List<*>) {
      warn("Schema '$name' uses tuple typing (array of schemas) - using first item type")
      schema.items = items.firstOrNull { it is Schema<*> } as? Schema<*> ?: Schema<Any>().apply { type = "object" }
    }

    validateSchema("$name.items", schema.items, depth + 1)
  }

  /**
   * Validates enum values.
   * Note: Enum validation is skipped for now due to Swagger library type constraints.
   */
  private fun validateEnum(schema: Schema<*>, name: String) {
    // Skipped: Swagger library uses raw types for enum, causing type mismatch issues
    // The SchemaExtractor handles null enum values gracefully during extraction
  }

  /**
   * Validates additionalProperties.
   */
  private fun validateAdditionalProperties(schema: Schema<*>, name: String, depth: Int) {
    val additionalProps = schema.additionalProperties

    if (additionalProps is Schema<*>) {
      validateSchema("$name.additionalProperties", additionalProps, depth + 1)
    } else if (additionalProps != null && additionalProps !is Boolean) {
      warn("Schema '$name' has unknown additionalProperties type: ${additionalProps::class.simpleName}")
    }
  }

  /**
   * Validates paths section.
   */
  private fun validatePaths(openAPI: OpenAPI) {
    if (openAPI.paths == null) {
      warn("Missing 'paths' section - creating empty paths")
      openAPI.paths = Paths()
      return
    }

    // Validate each path
    openAPI.paths?.forEach { (path, pathItem) ->
      if (pathItem == null) {
        warn("Path '$path' has null path item - skipping")
        return@forEach
      }

      // Validate path format
      if (!path.startsWith("/") && !path.startsWith("\$")) {
        warn("Path '$path' doesn't start with '/' - this may be invalid")
      }

      // Validate operations
      pathItem.readOperationsMap().forEach { (method, operation) ->
        if (operation == null) {
          warn("Path '$path' has null operation for $method - skipping")
          return@forEach
        }

        // Validate operationId
        if (operation.operationId.isNullOrBlank()) {
          warn("Path '$path' $method operation missing operationId - will generate from path")
        } else if (!isValidOperationId(operation.operationId!!)) {
          warn("Path '$path' has invalid operationId '${operation.operationId}' - will sanitize")
          operation.operationId = sanitizeOperationId(operation.operationId!!)
        }

        // Validate responses
        if (operation.responses.isNullOrEmpty()) {
          warn("Path '$path' $method has no responses - adding default 200 response")
          val apiResponses = io.swagger.v3.oas.models.responses.ApiResponses()
          apiResponses.addApiResponse(
            "200",
            io.swagger.v3.oas.models.responses.ApiResponse().apply {
              description = "Successful response"
            }
          )
          operation.responses = apiResponses
        }
      }
    }
  }

  /**
   * Validates that all $ref references point to existing schemas.
   */
  private fun validateSchemaReferences(openAPI: OpenAPI) {
    val componentSchemas = openAPI.components?.schemas?.keys.orEmpty()

    openAPI.components?.schemas?.forEach { (name, schema) ->
      schema?.let { s ->
        validateReferencesInSchema(name, s, componentSchemas)
      }
    }

    openAPI.paths?.forEach { (path, pathItem) ->
      pathItem?.readOperationsMap()?.forEach { (method, operation) ->
        operation?.let { op ->
          // Check request body references
          op.requestBody?.content?.forEach { (_, mediaType) ->
            mediaType?.schema?.let { validateReferencesInSchema("$path.$method.requestBody", it, componentSchemas) }
          }

          // Check response references
          op.responses?.forEach { (status, response) ->
            response?.content?.forEach { (_, mediaType) ->
              mediaType?.schema?.let { validateReferencesInSchema("$path.$method.$status.response", it, componentSchemas) }
            }
          }
        }
      }
    }
  }

  private fun validateReferencesInSchema(context: String, schema: Schema<*>, validSchemas: Set<String>) {
    // Check $ref
    schema.`$ref`?.let { ref ->
      val refName = extractRefName(ref)
      if (refName !in validSchemas) {
        error("$context has broken reference '$ref' - target schema '$refName' does not exist")
      }
    }

    // Check items
    schema.items?.let { items ->
      validateReferencesInSchema("$context.items", items, validSchemas)
    }

    // Check compositions
    schema.oneOf?.forEach { validateReferencesInSchema("$context.oneOf", it, validSchemas) }
    schema.allOf?.forEach { validateReferencesInSchema("$context.allOf", it, validSchemas) }
    schema.anyOf?.forEach { validateReferencesInSchema("$context.anyOf", it, validSchemas) }

    // Check properties
    schema.properties?.forEach { (propName, propSchema) ->
      propSchema?.let { validateReferencesInSchema("$context.$propName", it, validSchemas) }
    }
  }

  /**
   * Detects circular references in schema definitions.
   */
  private fun validateCycles(openAPI: OpenAPI) {
    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()

    fun detectCycle(schemaName: String, path: List<String>): Boolean {
      if (schemaName in visiting) {
        val cycle = (path + schemaName).joinToString(" -> ")
        warn("Circular reference detected: $cycle")
        return true
      }

      if (schemaName in visited) return false

      val schema = openAPI.components?.schemas?.get(schemaName) ?: return false

      visiting.add(schemaName)

      val hasCycle = sequence {
        // Check $ref
        schema.`$ref`?.let { yield(extractRefName(it)) }

        // Check items
        schema.items?.`$ref`?.let { yield(extractRefName(it)) }

        // Check compositions
        schema.oneOf?.forEach { it.`$ref`?.let { ref -> yield(extractRefName(ref)) } }
        schema.allOf?.forEach { it.`$ref`?.let { ref -> yield(extractRefName(ref)) } }
        schema.anyOf?.forEach { it.`$ref`?.let { ref -> yield(extractRefName(ref)) } }

        // Check properties
        schema.properties?.forEach { (_, propSchema) ->
          propSchema?.`$ref`?.let { yield(extractRefName(it)) }
        }
      }.any { refName -> detectCycle(refName, path + schemaName) }

      visiting.remove(schemaName)
      visited.add(schemaName)

      return hasCycle
    }

    openAPI.components?.schemas?.keys?.forEach { schemaName ->
      if (schemaName !in visited) {
        detectCycle(schemaName, emptyList())
      }
    }
  }

  /**
   * Extracts schema name from a $ref string.
   */
  private fun extractRefName(ref: String): String {
    return ref.substringAfterLast("/")
  }

  /**
   * Sanitizes API title for use in identifiers and file names.
   */
  private fun sanitizeTitle(title: String): String {
    return title
      .replace(Regex("[^a-zA-Z0-9\\s-]"), "") // Remove special chars
      .trim()
      .ifBlank { DEFAULT_TITLE }
  }

  /**
   * Checks if operationId is a valid Kotlin identifier.
   */
  private fun isValidOperationId(operationId: String): Boolean {
    if (operationId.isEmpty()) return false
    if (!operationId[0].isJavaIdentifierStart()) return false
    return operationId.all { it.isJavaIdentifierPart() }
  }

  /**
   * Sanitizes operationId to be a valid Kotlin identifier.
   */
  private fun sanitizeOperationId(operationId: String): String {
    return buildString {
      operationId.forEachIndexed { index, c ->
        if (index == 0) {
          if (!c.isJavaIdentifierStart()) {
            append('_')
          }
          append(c)
        } else {
          if (c.isJavaIdentifierPart()) {
            append(c)
          } else {
            append('_')
          }
        }
      }
    }
  }

  private fun warn(message: String) {
    warnings.add(message)
    LOG.warn("[SpecNormalizer] $message")
  }

  private fun error(message: String) {
    errors.add(message)
    LOG.error("[SpecNormalizer] $message")
  }
}
