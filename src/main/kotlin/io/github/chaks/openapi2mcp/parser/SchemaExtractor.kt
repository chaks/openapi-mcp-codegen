package io.github.chaks.openapi2mcp.parser

import io.github.chaks.openapi2mcp.parser.model.PropertyInfo
import io.github.chaks.openapi2mcp.parser.model.SchemaModel
import io.github.chaks.openapi2mcp.util.TypeMapper
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Extracts schema models from OpenAPI specification.
 *
 * Handles conversion of Swagger/OpenAPI Schema objects to internal SchemaModel representation,
 * including properties, enums, references, and polymorphic composition.
 */
@ApplicationScoped
class SchemaExtractor {

  @Inject
  private lateinit var typeMapper: TypeMapper

  private lateinit var currentOpenAPI: OpenAPI

  /**
   * Sets the current OpenAPI specification context.
   * This is needed for finding references within the spec.
   */
  fun setCurrentOpenAPI(openAPI: OpenAPI) {
    this.currentOpenAPI = openAPI
  }

  /**
   * Extracts all schemas from the OpenAPI components.
   *
   * @param openAPI The parsed OpenAPI specification
   * @return Map of schema names to SchemaModel
   */
  fun extractSchemas(openAPI: OpenAPI): Map<String, SchemaModel> {
    this.currentOpenAPI = openAPI
    val schemas = mutableMapOf<String, SchemaModel>()

    openAPI.components?.schemas?.forEach { (name, schema) ->
      schemas[name] = extractSchemaModel(name, schema)
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
  fun extractSchemaModel(name: String, schema: Schema<*>): SchemaModel {
    val properties = schema.properties?.mapValues { (propName, propSchema) ->
      extractPropertyInfo(propName, propSchema)
    } ?: emptyMap()

    val required = schema.required?.toSet() ?: emptySet()

    // Extract oneOf, allOf, anyOf references
    val oneOfRefs = schema.oneOf?.mapNotNull { extractRef(it, currentOpenAPI) }
    val allOfRefs = schema.allOf?.mapNotNull { extractRef(it, currentOpenAPI) }
    val anyOfRefs = schema.anyOf?.mapNotNull { extractRef(it, currentOpenAPI) }

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

  /**
   * Extracts property information from a schema.
   *
   * @param name The property name
   * @param schema The Swagger Schema object
   * @return The extracted PropertyInfo
   */
  fun extractPropertyInfo(name: String, schema: Schema<*>): PropertyInfo {
    val isArray = schema is io.swagger.v3.oas.models.media.ArraySchema ||
      schema.type == "array" ||
      schema.types?.contains("array") == true
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
    val oneOfRefs = schema.oneOf?.mapNotNull { extractRef(it, currentOpenAPI) }
    val allOfRefs = schema.allOf?.mapNotNull { extractRef(it, currentOpenAPI) }
    val anyOfRefs = schema.anyOf?.mapNotNull { extractRef(it, currentOpenAPI) }

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
