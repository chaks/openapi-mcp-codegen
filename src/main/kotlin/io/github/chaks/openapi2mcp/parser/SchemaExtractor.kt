package io.github.chaks.openapi2mcp.parser

import io.github.chaks.openapi2mcp.parser.model.PropertyInfo
import io.github.chaks.openapi2mcp.parser.model.SchemaModel
import io.github.chaks.openapi2mcp.util.TypeMapper
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Extracts schema models from OpenAPI specification.
 *
 * Handles conversion of Swagger/OpenAPI Schema objects to internal SchemaModel representation,
 * including properties, enums, references, and polymorphic composition.
 *
 * DEFENSIVE DESIGN: Applies safe defaults for malformed specs:
 * - Missing types are inferred from structure
 * - Null schemas become empty objects
 * - Broken references are logged and handled gracefully
 * - Circular references are detected and reported
 */
@ApplicationScoped
class SchemaExtractor {

  companion object {
    private val LOG = LoggerFactory.getLogger(SchemaExtractor::class.java)

    // Maximum depth for recursive schema extraction to prevent stack overflow
    private const val MAX_EXTRACTION_DEPTH = 100
  }

  @Inject
  private lateinit var typeMapper: TypeMapper

  private lateinit var currentOpenAPI: OpenAPI

  // Track schemas currently being extracted to detect cycles
  private val extractingSchemas = mutableSetOf<String>()

  // Track extraction depth to prevent stack overflow
  private var extractionDepth = 0

  /**
   * Sets the current OpenAPI specification context.
   * This is needed for finding references within the spec.
   */
  fun setCurrentOpenAPI(openAPI: OpenAPI) {
    this.currentOpenAPI = openAPI
    extractingSchemas.clear()
    extractionDepth = 0
  }

  /**
   * Extracts all schemas from the OpenAPI components.
   *
   * @param openAPI The parsed OpenAPI specification
   * @return Map of schema names to SchemaModel
   */
  fun extractSchemas(openAPI: OpenAPI): Map<String, SchemaModel> {
    this.currentOpenAPI = openAPI
    extractingSchemas.clear()
    extractionDepth = 0

    val schemas = mutableMapOf<String, SchemaModel>()

    // Handle null or empty schemas gracefully
    val componentSchemas = openAPI.components?.schemas
    if (componentSchemas == null) {
      LOG.warn("No component schemas found in OpenAPI spec")
      return schemas
    }

    componentSchemas.forEach { (name, schema) ->
      try {
        schemas[name] = extractSchemaModel(name, schema)
      } catch (e: Exception) {
        LOG.error("Failed to extract schema '$name': ${e.message}", e)
        // Apply safe default - create empty schema instead of failing completely
        schemas[name] = createSafeDefaultSchema(name, "Extraction failed: ${e.message}")
      }
    }

    return schemas
  }

  /**
   * Extracts a single schema model from a Swagger Schema object.
   *
   * @param name The schema name
   * @param schema The Swagger Schema object
   * @return The extracted SchemaModel
   */
  fun extractSchemaModel(name: String, schema: Schema<*>?): SchemaModel {
    // DEFENSIVE: Handle null schema
    if (schema == null) {
      LOG.warn("Schema '$name' is null - creating safe default")
      return createSafeDefaultSchema(name, "Schema was null in spec")
    }

    // DEFENSIVE: Check for circular reference
    if (name in extractingSchemas) {
      LOG.warn("Circular reference detected for schema '$name' - returning placeholder")
      return createSafeDefaultSchema(name, "Circular reference detected")
    }

    // DEFENSIVE: Check extraction depth
    if (extractionDepth > MAX_EXTRACTION_DEPTH) {
      LOG.error("Maximum extraction depth ($MAX_EXTRACTION_DEPTH) exceeded for schema '$name'")
      return createSafeDefaultSchema(name, "Maximum nesting depth exceeded")
    }

    extractionDepth++
    extractingSchemas.add(name)

    try {
      val properties = safeExtractProperties(schema)
      val required = schema.required?.toSet() ?: emptySet()

      // DEFENSIVE: Extract composition references with null checks
      val oneOfRefs = schema.oneOf?.safeMapNotNull { extractRef(it, currentOpenAPI) } ?: emptyList()
      val allOfRefs = schema.allOf?.safeMapNotNull { extractRef(it, currentOpenAPI) } ?: emptyList()
      val anyOfRefs = schema.anyOf?.safeMapNotNull { extractRef(it, currentOpenAPI) } ?: emptyList()

      // DEFENSIVE: Extract additionalProperties with type safety
      val additionalProperties = schema.additionalProperties?.let { additionalProps ->
        when (additionalProps) {
          is Schema<*> -> extractPropertyInfo("additionalProperties", additionalProps)
          is Boolean -> {
            // Boolean additionalProperties - create appropriate default
            if (additionalProps) {
              PropertyInfo(
                type = "any",
                format = null,
                description = "Additional properties allowed",
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
            } else {
              null // No additional properties allowed
            }
          }
          else -> {
            LOG.warn("Schema '$name' has unknown additionalProperties type: ${additionalProps::class.simpleName}")
            null
          }
        }
      }

      // DEFENSIVE: Safe type extraction - handle empty types list
      val schemaType = safeExtractType(schema, name)

      return SchemaModel(
        name = name,
        description = schema.description,
        type = schemaType,
        properties = properties,
        required = required,
        enum = schema.enum?.filterNotNull()?.map { it.toString() }?.ifEmpty { null },
        format = schema.format,
        ref = schema.`$ref`?.let { extractSimpleRef(it) },
        oneOf = oneOfRefs.ifEmpty { null },
        allOf = allOfRefs.ifEmpty { null },
        anyOf = anyOfRefs.ifEmpty { null },
        additionalProperties = additionalProperties
      )
    } finally {
      extractingSchemas.remove(name)
      extractionDepth--
    }
  }

  /**
   * Creates a safe default schema when extraction fails.
   */
  private fun createSafeDefaultSchema(name: String, reason: String): SchemaModel {
    return SchemaModel(
      name = name,
      description = "Safe default: $reason",
      type = "object",
      properties = emptyMap(),
      required = emptySet(),
      enum = null,
      format = null,
      ref = null,
      oneOf = null,
      allOf = null,
      anyOf = null,
      additionalProperties = null
    )
  }

  /**
   * Safely extracts type from schema, handling edge cases.
   */
  private fun safeExtractType(schema: Schema<*>, schemaName: String): String? {
    val explicitType = schema.type
    val typesList = schema.types

    return when {
      // Explicit type is set
      !explicitType.isNullOrBlank() -> explicitType

      // Handle types array (OpenAPI 3.1+ union types)
      !typesList.isNullOrEmpty() -> {
        val firstValidType = typesList.firstOrNull { !it.isNullOrBlank() }
        if (firstValidType == null) {
          LOG.warn("Schema '$schemaName' has empty 'types' array - defaulting to 'object'")
          "object"
        } else {
          firstValidType
        }
      }

      // Infer type from schema structure
      !schema.properties.isNullOrEmpty() -> "object"
      schema.items != null -> "array"
      !schema.enum.isNullOrEmpty() -> "string"
      schema.additionalProperties != null -> "object"
      !schema.oneOf.isNullOrEmpty() -> "object"
      !schema.allOf.isNullOrEmpty() -> "object"
      !schema.anyOf.isNullOrEmpty() -> "object"
      schema.format != null -> inferTypeFromFormat(schema.format)

      // Ultimate fallback
      else -> {
        LOG.warn("Schema '$schemaName' has no type - defaulting to 'object'")
        "object"
      }
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
   * Safely extracts properties from schema with null checks.
   */
  private fun safeExtractProperties(schema: Schema<*>): Map<String, PropertyInfo> {
    val properties = schema.properties
    if (properties.isNullOrEmpty()) {
      return emptyMap()
    }

    return properties.filterKeys { !it.isNullOrBlank() }.mapValues { (propName, propSchema) ->
      try {
        extractPropertyInfo(propName, propSchema)
      } catch (e: Exception) {
        LOG.error("Failed to extract property '$propName': ${e.message}")
        // Return safe default for property
        PropertyInfo(
          type = "any",
          format = null,
          description = "Property extraction failed: ${e.message}",
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
  }

  /**
   * Extracts property information from a schema.
   *
   * @param name The property name
   * @param schema The Swagger Schema object
   * @return The extracted PropertyInfo
   */
  fun extractPropertyInfo(name: String, schema: Schema<*>?): PropertyInfo {
    // DEFENSIVE: Handle null schema
    if (schema == null) {
      LOG.warn("Property '$name' has null schema - using Any type")
      return createSafeDefaultProperty("Property schema was null")
    }

    // DEFENSIVE: Safe array detection - handle edge cases
    val isArray = detectIsArray(schema)

    val arrayItemRef = if (isArray) {
      safeExtractArrayItemRef(schema, name)
    } else {
      null
    }

    val arrayItemType = if (isArray) {
      schema.items?.let { safeExtractType(it, "$name.items") }
    } else {
      null
    }

    val arrayItemFormat = if (isArray) {
      schema.items?.format
    } else {
      null
    }

    // DEFENSIVE: Extract composition references with null checks
    val oneOfRefs = schema.oneOf?.safeMapNotNull { extractRef(it, currentOpenAPI) }?.ifEmpty { null }
    val allOfRefs = schema.allOf?.safeMapNotNull { extractRef(it, currentOpenAPI) }?.ifEmpty { null }
    val anyOfRefs = schema.anyOf?.safeMapNotNull { extractRef(it, currentOpenAPI) }?.ifEmpty { null }

    // DEFENSIVE: Safe type and ref extraction
    val propertyType = safeExtractType(schema, name)
    val ref = schema.`$ref`?.let { extractSimpleRef(it) }
      ?: schema.let { findRefForSchema(it, currentOpenAPI) }

    return PropertyInfo(
      type = propertyType,
      format = schema.format,
      description = schema.description,
      isNullable = schema.nullable != false, // Default to nullable unless explicitly required
      ref = ref,
      isArray = isArray,
      arrayItemRef = arrayItemRef,
      arrayItemType = arrayItemType,
      arrayItemFormat = arrayItemFormat,
      enum = schema.enum?.filterNotNull()?.map { it.toString() }?.ifEmpty { null },
      defaultValue = schema.default?.toString(),
      oneOf = oneOfRefs,
      allOf = allOfRefs,
      anyOf = anyOfRefs
    )
  }

  /**
   * Creates a safe default property when extraction fails.
   */
  private fun createSafeDefaultProperty(reason: String): PropertyInfo {
    return PropertyInfo(
      type = "any",
      format = null,
      description = "Safe default: $reason",
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

  /**
   * DEFENSIVE: Safely detects if a schema represents an array.
   * Handles edge cases like empty types list, null type, etc.
   */
  private fun detectIsArray(schema: Schema<*>): Boolean {
    return schema is io.swagger.v3.oas.models.media.ArraySchema ||
      schema.type == "array" ||
      schema.types?.any { it == "array" } == true ||
      schema.items != null // If items is set, treat as array even without explicit type
  }

  /**
   * DEFENSIVE: Safely extracts array item reference with null handling.
   */
  private fun safeExtractArrayItemRef(schema: Schema<*>, propertyName: String): String? {
    val itemsSchema = schema.items

    // Handle null items
    if (itemsSchema == null) {
      LOG.warn("Array property '$propertyName' has no items schema - defaulting to Any")
      return null
    }

    // Handle tuple typing (items as list)
    if (itemsSchema is List<*>) {
      LOG.warn("Array property '$propertyName' uses tuple typing - using first item type")
      val firstItem = itemsSchema.firstOrNull { it is Schema<*> } as? Schema<*>
      return firstItem?.`$ref`?.let { extractSimpleRef(it) }
        ?: firstItem?.let { findRefForSchema(it, currentOpenAPI) }
    }

    // Standard case: items is a single schema
    return itemsSchema.`$ref`?.let { extractSimpleRef(it) }
      ?: findRefForSchema(itemsSchema, currentOpenAPI)
  }

  /**
   * Extension function for safe mapNotNull that handles null elements.
   */
  private fun <T, R> List<T?>.safeMapNotNull(transform: (T) -> R?): List<R> {
    return this.filterNotNull().mapNotNull(transform)
  }

  /**
   * Finds the reference name for a schema by matching against component schemas.
   *
   * @param schema The schema to find a reference for
   * @param openAPI The OpenAPI specification containing component schemas
   * @return The matching schema name, or null if not found
   */
  fun findRefForSchema(schema: Schema<*>, openAPI: OpenAPI): String? {
    if (schema.`$ref` != null) return extractSimpleRef(schema.`$ref`)

    // If ref is null, maybe it matches one of the component schemas
    openAPI.components?.schemas?.forEach { (name, componentSchema) ->
      if (schema == componentSchema) return name
    }
    return null
  }

  /**
   * Extracts a reference from a schema.
   *
   * @param schema The schema that may contain a reference
   * @param openAPI The OpenAPI specification context for finding references
   * @return The extracted reference name, or null if not a reference
   */
  fun extractRef(schema: Schema<*>, openAPI: OpenAPI): String? {
    return schema.`$ref`?.let { extractSimpleRef(it) }
      ?: findRefForSchema(schema, openAPI)
  }

  /**
   * Extracts a simple schema name from a full $ref path.
   *
   * @param ref The full reference path (e.g., "#/components/schemas/Name")
   * @return The simple schema name (e.g., "Name")
   */
  private fun extractSimpleRef(ref: String): String {
    // Extract simple name from #/components/schemas/Name
    val parts = ref.split("/")
    return parts.lastOrNull() ?: ref
  }
}
