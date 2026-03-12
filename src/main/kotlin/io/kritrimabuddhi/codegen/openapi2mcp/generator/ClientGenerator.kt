package io.kritrimabuddhi.codegen.openapi2mcp.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.kritrimabuddhi.codegen.openapi2mcp.cli.CliOptions
import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.ApiInfo
import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.ParameterInfo
import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.PathModel
import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.RequestBodyInfo
import io.kritrimabuddhi.codegen.openapi2mcp.util.TypeMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates REST client interfaces from OpenAPI path definitions.
 *
 * Uses KotlinPoet to generate interfaces with MicroProfile Rest Client
 * annotations for consuming REST APIs.
 */
@ApplicationScoped
class ClientGenerator {

  @Inject
  private lateinit var typeMapper: TypeMapper

  /**
   * Generate REST client interface.
   *
   * @param options CLI options containing output directory and package info
   * @param paths List of path models representing API endpoints
   * @param info API information (title, version)
   */
  fun generate(options: CliOptions, paths: List<PathModel>, info: ApiInfo) {
    val clientPackage = options.clientPackage
    val clientPath = getClientPath(options.output, clientPackage)

    if (!java.nio.file.Files.exists(clientPath)) {
      clientPath.createDirectories()
    }

    val fileSpec = generateClientInterface(
      clientPackage,
      options.clientName,
      options.configKey,
      options.domainPackage,
      paths,
      info
    )

    val outputPath = clientPath.resolve("${options.clientName}.kt")
    // Remove explicit 'public' modifiers (redundant in Kotlin)
    outputPath.writeText(fileSpec.toString().replace("public ", ""))
  }

  private fun getClientPath(outputDir: Path, clientPackage: String): Path {
    val packagePath = clientPackage.replace('.', '/')
    return outputDir.resolve("src/main/kotlin").resolve(packagePath)
  }

  private fun generateClientInterface(
    packageName: String,
    interfaceName: String,
    configKey: String,
    domainPackage: String,
    paths: List<PathModel>,
    info: ApiInfo
  ): FileSpec {
    val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)

    // Add @RegisterRestClient annotation
    interfaceBuilder.addAnnotation(
      AnnotationSpec.builder(
        ClassName("org.eclipse.microprofile.rest.client.inject", "RegisterRestClient")
      )
        .addMember("configKey = %S", configKey)
        .build()
    )

    // Add API documentation
    interfaceBuilder.addKdoc(buildInterfaceKdoc(info, paths.size))

    // Generate methods for each operation
    paths.forEach { path ->
      val methodSpec = generateClientMethod(path, packageName, domainPackage)
      interfaceBuilder.addFunction(methodSpec)
    }

    return FileSpec.builder(packageName, interfaceName)
      .addType(interfaceBuilder.build())
      .addImport("org.eclipse.microprofile.rest.client.inject", "RegisterRestClient")

      .addImport("jakarta.ws.rs.core", "Response")
      .addImport("jakarta.enterprise.context", "ApplicationScoped")
      .indent("    ")
      .build()
  }

  private fun generateClientMethod(path: PathModel, packageName: String, domainPackage: String): FunSpec {
    val methodName = deriveMethodName(path)
    val funBuilder = FunSpec.builder(methodName)
      .addModifiers(KModifier.ABSTRACT)

    // Add HTTP method annotation
    val httpMethodAnnotation = when (path.method.uppercase()) {
      "GET" -> ClassName("jakarta.ws.rs", "GET")
      "POST" -> ClassName("jakarta.ws.rs", "POST")
      "PUT" -> ClassName("jakarta.ws.rs", "PUT")
      "DELETE" -> ClassName("jakarta.ws.rs", "DELETE")
      "PATCH" -> ClassName("jakarta.ws.rs", "PATCH")
      "HEAD" -> ClassName("jakarta.ws.rs", "HEAD")
      "OPTIONS" -> ClassName("jakarta.ws.rs", "OPTIONS")
      else -> ClassName("jakarta.ws.rs", "GET")
    }
    funBuilder.addAnnotation(httpMethodAnnotation)

    // Add @Path annotation with the path template
    funBuilder.addAnnotation(
      AnnotationSpec.builder(ClassName("jakarta.ws.rs", "Path"))
        .addMember("%S", path.path)
        .build()
    )

    // Add KDoc
    funBuilder.addKdoc(buildMethodKdoc(path))

    // Add parameters
    path.parameters.forEach { param ->
      val paramName = typeMapper.toSafeIdentifier(param.name)

      val paramType = determineParameterType(param, domainPackage)
      val paramBuilder = ParameterSpec.builder(paramName, paramType)

      // Add appropriate parameter annotation
      when (param.`in`) {
        "path" -> {
          paramBuilder.addAnnotation(
            AnnotationSpec.builder(ClassName("jakarta.ws.rs", "PathParam"))
              .addMember("%S", param.name)
              .build()
          )
        }

        "query" -> {
          paramBuilder.addAnnotation(
            AnnotationSpec.builder(ClassName("jakarta.ws.rs", "QueryParam"))
              .addMember("%S", param.name)
              .build()
          )
        }

        "header" -> {
          paramBuilder.addAnnotation(
            AnnotationSpec.builder(ClassName("jakarta.ws.rs", "HeaderParam"))
              .addMember("%S", param.name)
              .build()
          )
        }

        "cookie" -> {
          paramBuilder.addAnnotation(
            AnnotationSpec.builder(ClassName("jakarta.ws.rs", "CookieParam"))
              .addMember("%S", param.name)
              .build()
          )
        }
      }

      // Add description as KDoc for the parameter
      if (param.description != null) {
        paramBuilder.addKdoc(param.description)
      }

      funBuilder.addParameter(paramBuilder.build())
    }

    // Add request body parameter if present
    path.requestBody?.let { requestBody ->
      val bodyType = determineRequestBodyType(requestBody, packageName, domainPackage)
      val bodyParam = ParameterSpec.builder("body", bodyType)
      if (requestBody.description != null) {
        bodyParam.addKdoc(requestBody.description)
      }
      funBuilder.addParameter(bodyParam.build())
    }

    // Determine return type
    val returnType = determineReturnType(path, packageName, domainPackage)
    funBuilder.returns(returnType)

    // Add suspend modifier if needed for async
    // funBuilder.addModifiers(KModifier.SUSPEND)

    return funBuilder.build()
  }

  private fun deriveMethodName(path: PathModel): String {
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

  private fun determineParameterType(param: ParameterInfo, domainPackage: String): com.squareup.kotlinpoet.TypeName {
    return when {
      param.isArray -> {
        val elementType = if (param.arrayItemRef != null) {
          ClassName(domainPackage, typeMapper.toPascalCase(param.arrayItemRef))
        } else {
          val mappedType = typeMapper.mapType(param.arrayItemType, param.arrayItemFormat)
          when (mappedType) {
            "String" -> ClassName("kotlin", "String")
            "Int" -> ClassName("kotlin", "Int")
            "Long" -> ClassName("kotlin", "Long")
            "Float" -> ClassName("kotlin", "Float")
            "Double" -> ClassName("kotlin", "Double")
            "Boolean" -> ClassName("kotlin", "Boolean")
            else -> ClassName("kotlin", "Any")
          }
        }
        ClassName("kotlin.collections", "List").parameterizedBy(elementType)
      }

      param.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(param.ref))
      }

      else -> {
        when (typeMapper.mapType(param.type, param.format)) {
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
    }.copy(nullable = !param.required)
  }

  private fun determineRequestBodyType(
    requestBody: RequestBodyInfo,
    packageName: String,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      requestBody.isArray -> {
        val baseType = when {
          requestBody.ref != null -> ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
          else -> {
            val mappedType = typeMapper.mapType(requestBody.arrayItemType, requestBody.arrayItemFormat)
            when (mappedType) {
              "String" -> ClassName("kotlin", "String")
              "Int" -> ClassName("kotlin", "Int")
              "Long" -> ClassName("kotlin", "Long")
              "Float" -> ClassName("kotlin", "Float")
              "Double" -> ClassName("kotlin", "Double")
              "Boolean" -> ClassName("kotlin", "Boolean")
              else -> ClassName("kotlin", "Any")
            }
          }
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      requestBody.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
      }

      else -> {
        val mappedType = typeMapper.mapType(requestBody.type, requestBody.format)
        when (mappedType) {
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
  }

  private fun determineReturnType(
    path: PathModel,
    packageName: String,
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
      successResponse?.isNoContent == true -> {
        ClassName("kotlin", "Unit")
      }

      successResponse?.isArray == true -> {
        val baseType = when {
          successResponse.ref != null -> ClassName(
            domainPackage,
            typeMapper.toPascalCase(successResponse.ref)
          )

          else -> {
            val mappedType =
              typeMapper.mapType(successResponse.arrayItemType, successResponse.arrayItemFormat)
            when (mappedType) {
              "String" -> ClassName("kotlin", "String")
              "Int" -> ClassName("kotlin", "Int")
              "Long" -> ClassName("kotlin", "Long")
              "Float" -> ClassName("kotlin", "Float")
              "Double" -> ClassName("kotlin", "Double")
              "Boolean" -> ClassName("kotlin", "Boolean")
              else -> ClassName("kotlin", "Any")
            }
          }
        }
        ClassName("kotlin.collections", "List").parameterizedBy(baseType)
      }

      successResponse?.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
      }

      else -> {
        val mappedType = typeMapper.mapType(successResponse?.type, successResponse?.format)
        when (mappedType) {
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
  }

  private fun buildInterfaceKdoc(info: ApiInfo, pathCount: Int): String {
    return buildString {
      appendLine("REST Client interface for: ${info.title}")
      appendLine()
      appendLine("Version: ${info.version}")
      if (info.description != null) {
        appendLine()
        appendLine(info.description)
      }
      appendLine()
      appendLine("This interface is generated from an OpenAPI specification.")
      appendLine("It provides methods to interact with the API endpoints.")
      appendLine()
      appendLine("Endpoints: $pathCount")
    }.trimIndent()
  }

  private fun buildMethodKdoc(path: PathModel): String {
    return buildString {
      if (path.summary != null) {
        appendLine(path.summary)
      }
      if (path.description != null) {
        if (path.summary != null) appendLine()
        appendLine(path.description)
      }
      if (path.tags.isNotEmpty()) {
        appendLine()
        appendLine("Tags: ${path.tags.joinToString()}")
      }
    }.trimIndent()
  }
}