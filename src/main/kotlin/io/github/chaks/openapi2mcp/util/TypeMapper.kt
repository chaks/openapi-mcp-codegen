package io.github.chaks.openapi2mcp.util

import jakarta.enterprise.context.ApplicationScoped

/**
 * Utility for mapping OpenAPI types to Kotlin types.
 *
 * Handles type conversions from OpenAPI 3.0/3.1 specification types
 * to appropriate Kotlin types for code generation.
 */
@ApplicationScoped
class TypeMapper {

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
   */
  fun mapType(type: String?, format: String?): String {
    val resolvedType = (type ?: inferTypeFromFormat(format))?.lowercase()
    if (resolvedType == null) return "Any"

    return when (resolvedType) {
      "string" -> mapStringType(format)
      "integer" -> mapIntegerType(format)
      "number" -> mapNumberType(format)
      "boolean" -> "Boolean"
      "array" -> "List<Any>"
      "object" -> "Map<String, Any>"
      else -> "Any"
    }
  }

  /**
   * Infers the OpenAPI type from the format if type is missing.
   */
  private fun inferTypeFromFormat(format: String?): String? {
    return when (format?.lowercase()) {
      "int32", "int64" -> "integer"
      "float", "double" -> "number"
      "date", "date-time", "email", "uri", "url", "uuid" -> "string"
      else -> null
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
   */
  fun toPascalCase(input: String): String {
    return input.split(Regex("[-_ .]"))
      .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }
  }

  /**
   * Converts a string to camelCase.
   *
   * @param input Input string (e.g., "user_name", "user-name", "UserName")
   * @return camelCase string (e.g., "userName")
   */
  fun toCamelCase(input: String): String {
    val pascal = toPascalCase(input)
    return pascal.replaceFirstChar { it.lowercase() }
  }

  /**
   * Generates a safe identifier name that is a valid Kotlin identifier.
   *
   * @param name Input name (e.g., "class", "package", "user-name")
   * @return Safe Kotlin identifier
   */
  fun toSafeIdentifier(name: String): String {
    // Escape Kotlin keywords
    val keywords = setOf(
      "package", "import", "class", "interface", "object", "enum", "open", "sealed",
      "abstract", "final", "data", "override", "fun", "val", "var", "get", "set",
      "it", "this", "super", "when", "if", "else", "try", "catch", "finally",
      "for", "while", "do", "return", "break", "continue", "throw", "in", "is",
      "as", "typealias", "this", "super", "companion", "init", "field", "property",
      "receiver", "param", "setparam", "delegate", "file", "reified", "where",
      "by", "lateinit", "tailrec", "operator", "infix", "inline", "external",
      "crossinline", "noinline", "vararg", "suspend", "yield", "actual", "expect",
      "public", "private", "protected", "internal", "out", "invariant", "inner",
      "annotation", "catch", "constructor", "dynamic", "enum", "false", "finally",
      "get", "import", "lateinit", "null", "sealed", "super", "true", "value",
      "volatile", "transient", "strictfp", "native", "default"
    )

    val baseName = toCamelCase(name)

    return if (baseName in keywords) {
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
}