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
import org.slf4j.LoggerFactory

/**
 * Shared type resolution logic for code generators.
 *
 * Extracts common type determination logic used by ClientGenerator and ToolGenerator
 * to avoid duplication and ensure consistent type mapping across generators.
 *
 * DEFENSIVE DESIGN:
 * - Unknown types default to Any
 * - Broken references handled gracefully
 * - Null responses handled safely
 * - Edge cases logged for debugging
 */
@ApplicationScoped
class TypeResolver {

  companion object {
    private val LOG = LoggerFactory.getLogger(TypeResolver::class.java)
  }

  @Inject
  private lateinit var typeMapper: TypeMapper

  /**
   * Determines the Kotlin type for a parameter.
   *
   * @param param The parameter info from OpenAPI spec
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   *
   * DEFENSIVE: Handles null refs, unknown types, and broken references
   */
  fun determineParameterType(
    param: ParameterInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return try {
      val baseType = when {
        // DEFENSIVE: Check polymorphic with null-safe check
        typeMapper.isPolymorphic(param.oneOf, param.allOf, param.anyOf) -> {
          val refName = param.ref ?: "Any"
          safeClassName(domainPackage, typeMapper.toPascalCase(refName))
        }

        param.isArray -> {
          val elementType = if (!param.arrayItemRef.isNullOrBlank()) {
            safeClassName(domainPackage, typeMapper.toPascalCase(param.arrayItemRef))
          } else {
            mapBasicType(param.arrayItemType, param.arrayItemFormat)
          }
          ClassName("kotlin.collections", "List").parameterizedBy(elementType)
        }

        // DEFENSIVE: Handle blank ref
        !param.ref.isNullOrBlank() -> {
          safeClassName(domainPackage, typeMapper.toPascalCase(param.ref))
        }

        else -> {
          mapBasicType(param.type, param.format)
        }
      }

      // DEFENSIVE: Only make non-required params nullable
      baseType.copy(nullable = !param.required)

    } catch (e: Exception) {
      LOG.warn("Failed to determine parameter type for '${param.name}': ${e.message}")
      ClassName("kotlin", "Any").copy(nullable = true)
    }
  }

  /**
   * Determines the Kotlin type for a request body.
   *
   * @param requestBody The request body info from OpenAPI spec
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   *
   * DEFENSIVE: Handles null refs, unknown types, and broken references
   */
  fun determineRequestBodyType(
    requestBody: RequestBodyInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return try {
      when {
        typeMapper.isPolymorphic(requestBody.oneOf, requestBody.allOf, requestBody.anyOf) -> {
          val refName = requestBody.ref ?: "Any"
          safeClassName(domainPackage, typeMapper.toPascalCase(refName))
        }

        requestBody.isArray -> {
          val baseType = when {
            !requestBody.ref.isNullOrBlank() -> safeClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
            else -> mapBasicType(requestBody.arrayItemType, requestBody.arrayItemFormat)
          }
          ClassName("kotlin.collections", "List").parameterizedBy(baseType)
        }

        !requestBody.ref.isNullOrBlank() -> {
          safeClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
        }

        else -> {
          mapBasicType(requestBody.type, requestBody.format)
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to determine request body type: ${e.message}")
      ClassName("kotlin", "Any")
    }
  }

  /**
   * Determines the Kotlin type for a response.
   *
   * @param response The response info from OpenAPI spec (nullable)
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName
   *
   * DEFENSIVE: Handles null responses, unknown types, and broken references
   */
  fun determineResponseType(
    response: ResponseInfo?,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return try {
      when {
        response == null -> {
          LOG.debug("Null response - defaulting to Any")
          ClassName("kotlin", "Any")
        }

        typeMapper.isPolymorphic(response.oneOf, response.allOf, response.anyOf) -> {
          val refName = response.ref ?: "Any"
          safeClassName(domainPackage, typeMapper.toPascalCase(refName))
        }

        response.isNoContent -> {
          ClassName("kotlin", "Any")
        }

        response.isArray == true -> {
          val baseType = when {
            !response.ref.isNullOrBlank() -> safeClassName(domainPackage, typeMapper.toPascalCase(response.ref))
            else -> mapBasicType(response.arrayItemType, response.arrayItemFormat)
          }
          ClassName("kotlin.collections", "List").parameterizedBy(baseType)
        }

        !response.ref.isNullOrBlank() -> {
          safeClassName(domainPackage, typeMapper.toPascalCase(response.ref))
        }

        else -> {
          mapBasicType(response.type, response.format)
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to determine response type: ${e.message}")
      ClassName("kotlin", "Any")
    }
  }

  /**
   * Determines the return type for a REST client method.
   *
   * @param path The path model containing response definitions
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName for the method return type
   *
   * DEFENSIVE: Handles missing responses, unknown types, and broken references
   */
  fun determineClientReturnType(
    path: PathModel,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return try {
      // Check for 204 No Content response
      if (path.responses["204"] != null) {
        return ClassName("kotlin", "Unit")
      }

      // DEFENSIVE: Find success response with null-safe handling
      val successResponse = findSuccessResponse(path.responses)

      when {
        successResponse == null -> {
          LOG.debug("No success response found for ${path.method} ${path.path} - defaulting to Any")
          ClassName("kotlin", "Any")
        }

        typeMapper.isPolymorphic(successResponse.oneOf, successResponse.allOf, successResponse.anyOf) -> {
          val refName = successResponse.ref ?: "Any"
          safeClassName(domainPackage, typeMapper.toPascalCase(refName))
        }

        successResponse.isNoContent -> {
          ClassName("kotlin", "Unit")
        }

        successResponse.isArray == true -> {
          val baseType = when {
            !successResponse.ref.isNullOrBlank() -> safeClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
            else -> mapBasicType(successResponse.arrayItemType, successResponse.arrayItemFormat)
          }
          ClassName("kotlin.collections", "List").parameterizedBy(baseType)
        }

        !successResponse.ref.isNullOrBlank() -> {
          safeClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
        }

        else -> {
          mapBasicType(successResponse.type, successResponse.format)
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to determine client return type: ${e.message}")
      ClassName("kotlin", "Any")
    }
  }

  /**
   * Determines the return type for an MCP tool method.
   *
   * @param path The path model containing response definitions
   * @param domainPackage The domain package name for referenced types
   * @return The corresponding KotlinPoet TypeName for the tool method return type
   *
   * DEFENSIVE: Handles missing responses, unknown types, and broken references
   */
  fun determineToolReturnType(
    path: PathModel,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return try {
      // Check for 204 No Content response - return String for MCP
      if (path.responses["204"] != null) {
        return ClassName("kotlin", "String")
      }

      // DEFENSIVE: Find success response with null-safe handling
      val successResponse = findSuccessResponse(path.responses)

      when {
        successResponse == null -> {
          LOG.debug("No success response found for ${path.method} ${path.path} - defaulting to Any")
          ClassName("kotlin", "Any")
        }

        typeMapper.isPolymorphic(successResponse.oneOf, successResponse.allOf, successResponse.anyOf) -> {
          val refName = successResponse.ref ?: "Any"
          safeClassName(domainPackage, typeMapper.toPascalCase(refName))
        }

        successResponse.isNoContent -> {
          ClassName("kotlin", "String")
        }

        successResponse.isArray == true -> {
          val baseType = when {
            !successResponse.ref.isNullOrBlank() -> safeClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
            else -> mapBasicType(successResponse.arrayItemType, successResponse.arrayItemFormat)
          }
          ClassName("kotlin.collections", "List").parameterizedBy(baseType)
        }

        !successResponse.ref.isNullOrBlank() -> {
          safeClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
        }

        else -> {
          mapBasicType(successResponse.type, successResponse.format)
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to determine tool return type: ${e.message}")
      ClassName("kotlin", "Any")
    }
  }

  /**
   * DEFENSIVE: Finds the success response (2xx) from a response map.
   * Falls back to 'default' response if no 2xx found.
   */
  private fun findSuccessResponse(responses: Map<String, ResponseInfo>): ResponseInfo? {
    // Try to find 2xx response
    val successResponse = responses.entries
      .firstOrNull { it.key.matches(Regex("^2\\d{2}$")) }
      ?.value

    if (successResponse != null) {
      return successResponse
    }

    // DEFENSIVE: Fall back to default response
    return responses["default"]
  }

  /**
   * DEFENSIVE: Creates a ClassName with validation.
   * Ensures the class name is valid before creation.
   */
  private fun safeClassName(packageName: String, simpleName: String): ClassName {
    // DEFENSIVE: Validate simple name
    val validName = if (simpleName.isBlank() || simpleName == "Any") {
      "Any"
    } else {
      simpleName
    }
    return ClassName(packageName, validName)
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
   *
   * DEFENSIVE: Handles unknown types and formats gracefully
   */
  private fun mapBasicType(type: String?, format: String?): ClassName {
    return try {
      val mappedType = typeMapper.mapType(type, format)
      when (mappedType) {
        "String" -> ClassName("kotlin", "String")
        "Int" -> ClassName("kotlin", "Int")
        "Long" -> ClassName("kotlin", "Long")
        "Float" -> ClassName("kotlin", "Float")
        "Double" -> ClassName("kotlin", "Double")
        "Boolean" -> ClassName("kotlin", "Boolean")
        "ByteArray" -> ClassName("kotlin", "ByteArray")
        // DEFENSIVE: Unknown types default to Any
        else -> {
          LOG.debug("Unknown mapped type '$mappedType' - defaulting to Any")
          ClassName("kotlin", "Any")
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to map basic type '$type/$format': ${e.message}")
      ClassName("kotlin", "Any")
    }
  }
}
