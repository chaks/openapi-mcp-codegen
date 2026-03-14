package io.github.chaks.openapi2mcp.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.chaks.openapi2mcp.parser.model.ParameterInfo
import io.github.chaks.openapi2mcp.parser.model.PathModel
import io.github.chaks.openapi2mcp.parser.model.RequestBodyInfo
import io.github.chaks.openapi2mcp.parser.model.ResponseInfo
import io.github.chaks.openapi2mcp.util.TypeMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Shared type resolution logic for code generators.
 *
 * Extracts common type determination logic used by ClientGenerator and ToolGenerator
 * to avoid duplication and ensure consistent type mapping across generators.
 */
@ApplicationScoped
class TypeResolver {

  @Inject
  private lateinit var typeMapper: TypeMapper

  /**
   * Determines the Kotlin type for a parameter.
   *
   * @param param The parameter info from OpenAPI spec
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   */
  fun determineParameterType(
    param: ParameterInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      typeMapper.isPolymorphic(param.oneOf, param.allOf, param.anyOf) -> {
        ClassName(domainPackage, typeMapper.toPascalCase(param.ref ?: "Any"))
      }

      param.isArray -> {
        val elementType = if (param.arrayItemRef != null) {
          ClassName(domainPackage, typeMapper.toPascalCase(param.arrayItemRef))
        } else {
          mapBasicType(param.arrayItemType, param.arrayItemFormat)
        }
        ClassName("kotlin.collections", "List").parameterizedBy(elementType)
      }

      param.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(param.ref))
      }

      else -> {
        mapBasicType(param.type, param.format)
      }
    }.copy(nullable = !param.required)
  }

  /**
   * Determines the Kotlin type for a request body.
   *
   * @param requestBody The request body info from OpenAPI spec
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   */
  fun determineRequestBodyType(
    requestBody: RequestBodyInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      typeMapper.isPolymorphic(requestBody.oneOf, requestBody.allOf, requestBody.anyOf) -> {
        ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref ?: "Any"))
      }

      requestBody.isArray -> {
        val baseType = when {
          requestBody.ref != null -> ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
          else -> mapBasicType(requestBody.arrayItemType, requestBody.arrayItemFormat)
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      requestBody.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
      }

      else -> {
        mapBasicType(requestBody.type, requestBody.format)
      }
    }
  }

  /**
   * Determines the Kotlin type for a response.
   *
   * @param response The response info from OpenAPI spec (nullable)
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   */
  fun determineResponseType(
    response: ResponseInfo?,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      response == null -> ClassName("kotlin", "Any")
      typeMapper.isPolymorphic(response.oneOf, response.allOf, response.anyOf) -> {
        ClassName(domainPackage, typeMapper.toPascalCase(response.ref ?: "Any"))
      }

      response.isNoContent -> {
        ClassName("kotlin", "Any")
      }

      response.isArray == true -> {
        val baseType = when {
          response.ref != null -> ClassName(domainPackage, typeMapper.toPascalCase(response.ref))
          else -> mapBasicType(response.arrayItemType, response.arrayItemFormat)
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      response.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(response.ref))
      }

      else -> {
        mapBasicType(response.type, response.format)
      }
    }
  }

  /**
   * Determines the return type for a REST client method.
   *
   * @param path The path model containing response definitions
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName for the method return type
   */
  fun determineClientReturnType(
    path: PathModel,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    // Check for 204 No Content response
    if (path.responses["204"] != null) {
      return ClassName("kotlin", "Unit")
    }

    // Find the success response (2xx)
    val successResponse = path.responses.entries
      .firstOrNull { it.key.matches(Regex("^2\\d\\d$")) }
      ?.value
      ?: path.responses["default"]

    return when {
      successResponse == null -> ClassName("kotlin", "Any")
      typeMapper.isPolymorphic(successResponse.oneOf, successResponse.allOf, successResponse.anyOf) -> {
        ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref ?: "Any"))
      }

      successResponse.isNoContent -> {
        ClassName("kotlin", "Unit")
      }

      successResponse.isArray == true -> {
        val baseType = when {
          successResponse.ref != null -> ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
          else -> mapBasicType(successResponse.arrayItemType, successResponse.arrayItemFormat)
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      successResponse.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
      }

      else -> {
        mapBasicType(successResponse.type, successResponse.format)
      }
    }
  }

  /**
   * Determines the return type for an MCP tool method.
   *
   * @param path The path model containing response definitions
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName for the tool method return type
   */
  fun determineToolReturnType(
    path: PathModel,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    // Check for 204 No Content response
    if (path.responses["204"] != null) {
      return ClassName("kotlin", "String")
    }

    // Find the success response (2xx)
    val successResponse = path.responses.entries
      .firstOrNull { it.key.matches(Regex("^2\\d\\d$")) }
      ?.value
      ?: path.responses["default"]

    return when {
      successResponse == null -> ClassName("kotlin", "Any")
      typeMapper.isPolymorphic(successResponse.oneOf, successResponse.allOf, successResponse.anyOf) -> {
        ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref ?: "Any"))
      }

      successResponse.isNoContent -> {
        ClassName("kotlin", "String")
      }

      successResponse.isArray == true -> {
        val baseType = when {
          successResponse.ref != null -> ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
          else -> mapBasicType(successResponse.arrayItemType, successResponse.arrayItemFormat)
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      successResponse.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
      }

      else -> {
        mapBasicType(successResponse.type, successResponse.format)
      }
    }
  }

  /**
   * Derives a method name from the path model.
   *
   * Uses operationId if available, otherwise derives from path and method.
   *
   * @param path The path model
   * @return A camelCase method name
   */
  fun deriveMethodName(path: PathModel): String {
    // Try to use operationId if available
    path.operationId?.let { return typeMapper.toCamelCase(it) }

    // Otherwise derive from path and method
    val pathPart = path.path
      .removePrefix("/")
      .removeSuffix("/")
      .split("/")
      .filterNot { it.startsWith("{") && it.endsWith("}") }
      .joinToString("_")

    val methodPart = path.method.lowercase()

    return if (pathPart.isNotEmpty()) {
      typeMapper.toCamelCase("${methodPart}_$pathPart")
    } else {
      methodPart
    }
  }

  /**
   * Maps a basic OpenAPI type to a KotlinPoet TypeName.
   *
   * @param type The OpenAPI type
   * @param format The optional format
   * @return The corresponding KotlinPoet ClassName
   */
  private fun mapBasicType(type: String?, format: String?): ClassName {
    val mappedType = typeMapper.mapType(type, format)
    return when (mappedType) {
      "String" -> ClassName("kotlin", "String")
      "Int" -> ClassName("kotlin", "Int")
      "Long" -> ClassName("kotlin", "Long")
      "Float" -> ClassName("kotlin", "Float")
      "Double" -> ClassName("kotlin", "Double")
      "Boolean" -> ClassName("kotlin", "Boolean")
      "ByteArray" -> ClassName("kotlin", "ByteArray")
      else -> ClassName("kotlin", "Any")
    }
  }
}
