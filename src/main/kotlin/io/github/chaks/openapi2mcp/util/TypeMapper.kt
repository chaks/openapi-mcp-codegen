package io.github.chaks.openapi2mcp.util

import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

/**
 * Utility for mapping OpenAPI types to Kotlin types.
 *
 * Handles type conversions from OpenAPI 3.0/3.1 specification types
 * to appropriate Kotlin types for code generation.
 *
 * DEFENSIVE DESIGN:
 * - Unknown types default to Any
 * - Invalid identifiers are sanitized
 * - Null/empty inputs handled gracefully
 * - Edge cases logged for debugging
 */
@ApplicationScoped
class TypeMapper {

  companion object {
    private val LOG = LoggerFactory.getLogger(TypeMapper::class.java)

    // Reserved Kotlin keywords that must be escaped
    private val KOTLIN_KEYWORDS = setOf(
      // Hard keywords
      "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
      "in", "interface", "is", "null", "object", "package", "return", "super",
      "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when",
      "while",

      // Soft keywords
      "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
      "finally", "get", "import", "init", "param", "property", "receiver", "set",
      "setparam", "where",

      // Modifier keywords
      "actual", "abstract", "annotation", "companion", "const", "crossinline",
      "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
      "internal", "lateinit", "noinline", "open", "operator", "out", "override",
      "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
      "value", "vararg",

      // Other reserved identifiers
      "it", "yield", "assert", "default"
    )
  }

  /**
   * Maps an OpenAPI schema reference to a Kotlin class name.
   *
   * @param ref The simple schema name (extracted from $ref)
   * @return Kotlin class name
   */
  fun mapRef(ref: String): String {
    return toPascalCase(ref)
  }

  /**
   * Maps an OpenAPI property type to a Kotlin type name.
   *
   * @param type The OpenAPI type (string, number, integer, boolean, array, object)
   * @param format Optional format (e.g., int32, int64, float, double, date-time)
   * @return Kotlin type name
   *
   * DEFENSIVE: Handles null/empty types, unknown formats, and edge cases
   */
  fun mapType(type: String?, format: String?): String {
    // DEFENSIVE: Handle null or blank type
    val resolvedType = if (type.isNullOrBlank()) {
      // Try to infer from format
      inferTypeFromFormat(format) ?: run {
        LOG.debug("Type is null/blank, defaulting to 'Any'")
        "Any"
      }
    } else {
      type.lowercase().trim()
    }

    // DEFENSIVE: Validate type is reasonable
    if (resolvedType.length > 100) {
      LOG.warn("Unusually long type name '$resolvedType' - truncating and defaulting to Any")
      return "Any"
    }

    return when (resolvedType) {
      "string" -> mapStringType(format)
      "integer" -> mapIntegerType(format)
      "number" -> mapNumberType(format)
      "boolean" -> "Boolean"
      "array" -> "List<Any>" // Array without items defined
      "object" -> "Map<String, Any>"
      "null" -> "Any?" // OpenAPI 3.1 null type
      else -> {
        // DEFENSIVE: Unknown type - log and default to Any
        LOG.debug("Unknown type '$resolvedType' with format '$format' - defaulting to Any")
        "Any"
      }
    }
  }

  /**
   * Infers the OpenAPI type from the format if type is missing.
   *
   * DEFENSIVE: Handles null and unknown formats gracefully
   */
  private fun inferTypeFromFormat(format: String?): String? {
    if (format.isNullOrBlank()) return null

    return when (format.lowercase().trim()) {
      "int32", "int64" -> "integer"
      "float", "double" -> "number"
      "date", "date-time", "email", "uri", "url", "uuid",
      "byte", "binary", "password", "hostname", "ipv4", "ipv6" -> "string"
      else -> {
        LOG.debug("Unknown format '$format' - cannot infer type")
        null
      }
    }
  }

  /**
   * Maps an OpenAPI string format to a Kotlin type.
   *
   * @param format Optional format
   * @return Kotlin type name
   */
  private fun mapStringType(format: String?): String {
    return when (format) {
      "date", "date-time" -> "String" // Could use java.time.LocalDateTime
      "email", "uri", "url", "uuid" -> "String"
      "binary" -> "ByteArray"
      "byte" -> "String" // base64 encoded
      else -> "String"
    }
  }

  /**
   * Maps an OpenAPI integer format to a Kotlin type.
   *
   * @param format Optional format
   * @return Kotlin type name
   */
  private fun mapIntegerType(format: String?): String {
    return when (format) {
      "int32" -> "Int"
      "int64" -> "Long"
      else -> "Int"
    }
  }

  /**
   * Maps an OpenAPI number format to a Kotlin type.
   *
   * @param format Optional format
   * @return Kotlin type name
   */
  private fun mapNumberType(format: String?): String {
    return when (format) {
      "float" -> "Float"
      "double" -> "Double"
      else -> "Double"
    }
  }

  /**
   * Maps an OpenAPI array to a Kotlin List type.
   *
   * @param itemType The type of items in the array
   * @param itemRef Optional reference to schema for array items
   * @return Kotlin List type
   */
  fun mapArrayType(itemType: String?, itemRef: String?): String {
    val elementType = if (itemRef != null) {
      mapRef(itemRef)
    } else {
      mapType(itemType, null)
    }
    return "List<$elementType>"
  }

  /**
   * Converts a string to PascalCase.
   *
   * @param input Input string (e.g., "user_name", "user-name", "userName")
   * @return PascalCase string (e.g., "UserName")
   *
   * DEFENSIVE: Handles null, empty, and pathological inputs
   */
  fun toPascalCase(input: String?): String {
    // DEFENSIVE: Handle null or empty input
    if (input.isNullOrBlank()) {
      LOG.debug("Empty input to toPascalCase - returning 'Any'")
      return "Any"
    }

    // DEFENSIVE: Sanitize input - remove invalid characters
    val sanitized = input.trim()
      .replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "_") // Replace invalid chars with underscore
      .replace(Regex("_+"), "_") // Collapse multiple underscores

    if (sanitized.isBlank()) {
      LOG.debug("Input '$input' sanitized to empty - returning 'Any'")
      return "Any"
    }

    // DEFENSIVE: Handle strings that start with numbers
    val prefix = if (sanitized.firstOrNull()?.isDigit() == true) "_" else ""

    return prefix + sanitized.split(Regex("[-_.\\s]"))
      .filter { it.isNotEmpty() }
      .joinToString("") { part ->
        part.replaceFirstChar { c ->
          if (c.isLowerCase()) c.uppercaseChar() else c
        }
      }
      .ifBlank { "Any" }
  }

  /**
   * Converts a string to camelCase.
   *
   * @param input Input string (e.g., "user_name", "user-name", "UserName")
   * @return camelCase string (e.g., "userName")
   *
   * DEFENSIVE: Handles null, empty, and pathological inputs
   */
  fun toCamelCase(input: String?): String {
    // DEFENSIVE: Handle null or empty input
    if (input.isNullOrBlank()) {
      LOG.debug("Empty input to toCamelCase - returning 'any'")
      return "any"
    }

    val pascal = toPascalCase(input)
    return pascal.replaceFirstChar { it.lowercase() }
  }

  /**
   * Generates a safe identifier name that is a valid Kotlin identifier.
   *
   * @param name Input name (e.g., "class", "package", "user-name")
   * @return Safe Kotlin identifier
   *
   * DEFENSIVE: Escapes keywords, handles invalid characters, ensures non-empty result
   */
  fun toSafeIdentifier(name: String?): String {
    // DEFENSIVE: Handle null or empty input
    if (name.isNullOrBlank()) {
      LOG.debug("Empty name - returning 'value'")
      return "value"
    }

    val baseName = toCamelCase(name)

    // DEFENSIVE: Ensure we have a valid identifier
    if (baseName.isBlank() || baseName == "any") {
      return "value"
    }

    // DEFENSIVE: Escape Kotlin keywords with backticks
    return if (baseName in KOTLIN_KEYWORDS) {
      "`$baseName`"
    } else {
      baseName
    }
  }

  /**
   * Determines if a type is nullable based on whether it's in the required set.
   *
   * @param propertyName Property name
   * @param required Set of required property names
   * @return True if the property should be nullable
   */
  fun isNullable(propertyName: String, required: Set<String>): Boolean {
    return propertyName !in required
  }

  /**
   * Checks if a type uses polymorphic composition.
   *
   * @param oneOf List of oneOf references
   * @param allOf List of allOf references
   * @param anyOf List of anyOf references
   * @return True if any composition is present
   */
  fun isPolymorphic(
    oneOf: List<String>?,
    allOf: List<String>?,
    anyOf: List<String>?
  ): Boolean {
    return !oneOf.isNullOrEmpty() || !allOf.isNullOrEmpty() || !anyOf.isNullOrEmpty()
  }

  /**
   * Generates a type name for polymorphic schemas.
   *
   * @param oneOf List of oneOf references
   * @param allOf List of allOf references
   * @param anyOf List of anyOf references
   * @return Appropriate type name for the composition
   */
  fun mapPolymorphicType(
    oneOf: List<String>?,
    allOf: List<String>?,
    anyOf: List<String>?
  ): String? {
    return when {
      !oneOf.isNullOrEmpty() -> oneOf.joinToString("Or") { toPascalCase(it) }
      !allOf.isNullOrEmpty() -> allOf.joinToString("And") { toPascalCase(it) }
      !anyOf.isNullOrEmpty() -> anyOf.joinToString("Or") { toPascalCase(it) }
      else -> null
    }
  }
}