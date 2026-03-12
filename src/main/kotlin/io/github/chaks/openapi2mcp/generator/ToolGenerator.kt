package io.github.chaks.openapi2mcp.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.chaks.openapi2mcp.cli.CliOptions
import io.github.chaks.openapi2mcp.parser.model.ApiInfo
import io.github.chaks.openapi2mcp.parser.model.PathModel
import io.github.chaks.openapi2mcp.util.TypeMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates MCP tool wrapper classes from OpenAPI path definitions.
 *
 * Uses KotlinPoet to generate classes with Quarkus LangChain @Tool and @ToolArg annotations
 * that wrap REST client calls for use in MCP (Model Context Protocol).
 */
@ApplicationScoped
class ToolGenerator {

  @Inject
  private lateinit var typeMapper: TypeMapper

  /**
   * Generate MCP tool class.
   *
   * @param options CLI options containing output directory and package info
   * @param paths List of path models representing API endpoints
   * @param info API information (title, version)
   */
  fun generate(options: CliOptions, paths: List<PathModel>, info: ApiInfo) {
    val toolPackage = options.toolPackage
    val toolPath = getToolPath(options.output, toolPackage)

    if (!java.nio.file.Files.exists(toolPath)) {
      toolPath.createDirectories()
    }

    val fileSpec = generateToolClass(
      toolPackage,
      options.toolName,
      options.clientName,
      options.clientPackage,
      options.domainPackage,
      paths,
      info
    )

    val outputPath = toolPath.resolve("${options.toolName}.kt")
    // Remove explicit 'public' modifiers (redundant in Kotlin)
    outputPath.writeText(fileSpec.toString().replace("public ", ""))
  }

  private fun getToolPath(outputDir: Path, toolPackage: String): Path {
    val packagePath = toolPackage.replace('.', '/')
    return outputDir.resolve("src/main/kotlin").resolve(packagePath)
  }

  private fun generateToolClass(
    packageName: String,
    className: String,
    clientName: String,
    clientPackage: String,
    domainPackage: String,
    paths: List<PathModel>,
    info: ApiInfo
  ): FileSpec {
    val classBuilder = TypeSpec.classBuilder(className)

    // Add @ApplicationScoped annotation
    classBuilder.addAnnotation(
      ClassName("jakarta.enterprise.context", "ApplicationScoped")
    )

    // Add class documentation
    classBuilder.addKdoc(buildToolClassKdoc(info, paths.size))

    // Add REST client field
    val clientTypeName = ClassName(clientPackage, clientName)
    classBuilder.addProperty(
      com.squareup.kotlinpoet.PropertySpec.builder("client", clientTypeName)
        .addModifiers(com.squareup.kotlinpoet.KModifier.LATEINIT)
        .addAnnotation(
          AnnotationSpec.builder(
            ClassName("org.eclipse.microprofile.rest.client.inject", "RestClient")
          ).build()
        )
        .addKdoc("Injected REST client for API calls")
        .mutable(true)
        .build()
    )

    // Generate tool methods for each operation
    paths.forEach { path ->
      val methodSpec = generateToolMethod(path, clientPackage, clientName, domainPackage)
      classBuilder.addFunction(methodSpec)
    }

    return FileSpec.builder(packageName, className)
      .addType(classBuilder.build())
      .addImport("jakarta.enterprise.context", "ApplicationScoped")
      .addImport("org.eclipse.microprofile.rest.client.inject", "RestClient")
      .addImport("io.quarkiverse.mcp.server", "Tool")
      .addImport("io.quarkiverse.mcp.server", "ToolArg")
      .indent("    ")
      .build()
  }

  private fun generateToolMethod(
    path: PathModel,
    clientPackage: String,
    clientName: String,
    domainPackage: String
  ): FunSpec {
    val methodName = deriveMethodName(path)
    val funBuilder = FunSpec.builder(methodName)

    // Add @Tool annotation with description
    val toolDescription = buildToolDescription(path)
    funBuilder.addAnnotation(
      AnnotationSpec.builder(ClassName("io.quarkiverse.mcp.server", "Tool"))
        .addMember("description = %S", toolDescription)
        .build()
    )

    // Add method documentation
    funBuilder.addKdoc(buildMethodKdoc(path))

    // Add parameters
    path.parameters.forEach { param ->
      val paramName = typeMapper.toSafeIdentifier(param.name)
      val paramType = determineParameterType(param, domainPackage)

      val paramBuilder = ParameterSpec.builder(paramName, paramType)

      // Add @ToolArg annotation
      val paramDescription = if (param.description.isNullOrBlank()) paramName else param.description
      paramBuilder.addAnnotation(
        AnnotationSpec.builder(ClassName("io.quarkiverse.mcp.server", "ToolArg"))
          .addMember("description = %S", paramDescription)
          .build()
      )

      // Add parameter description to KDoc
      paramBuilder.addKdoc(paramDescription)

      funBuilder.addParameter(paramBuilder.build())
    }

    // Add request body parameter if present
    path.requestBody?.let { requestBody ->
      val bodyType = determineRequestBodyType(requestBody, domainPackage)
      val bodyParam = ParameterSpec.builder("body", bodyType)
      val bodyDescription = if (requestBody.description.isNullOrBlank()) "Request body" else requestBody.description

      bodyParam.addAnnotation(
        AnnotationSpec.builder(ClassName("io.quarkiverse.mcp.server", "ToolArg"))
          .addMember("description = %S", bodyDescription)
          .build()
      )

      bodyParam.addKdoc(bodyDescription)
      funBuilder.addParameter(bodyParam.build())
    }

    // Determine return type
    val returnType = determineReturnType(path, domainPackage)
    funBuilder.returns(returnType)

    // Add method body that calls the REST client
    funBuilder.addStatement(buildClientCall(path, methodName))

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

  private fun buildToolDescription(path: PathModel): String {
    val parts = mutableListOf<String>()

    // Add HTTP method and path
    parts.add("${path.method.uppercase()} ${path.path}")

    // Add summary if available
    path.summary?.let { parts.add(it) }

    // Add description if available and different from summary
    if (path.description != null && path.description != path.summary) {
      parts.add(path.description)
    }

    return parts.joinToString(" - ")
  }

  private fun determineParameterType(
    param: io.github.chaks.openapi2mcp.parser.model.ParameterInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
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
    requestBody: io.github.chaks.openapi2mcp.parser.model.RequestBodyInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      requestBody.isArray -> {
        val baseType = if (requestBody.ref != null) {
          ClassName(domainPackage, typeMapper.toPascalCase(requestBody.ref))
        } else {
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

  private fun determineReturnType(path: PathModel, domainPackage: String): com.squareup.kotlinpoet.TypeName {
    // Check for 204 No Content response
    if (path.responses["204"] != null) {
      return ClassName("kotlin", "String") // Return message for MCP
    }

    // Find the success response (2xx)
    val successResponse = path.responses.entries
      .firstOrNull { it.key.matches(Regex("^2\\d\\d$")) }
      ?.value
      ?: path.responses["default"]

    return when {
      successResponse?.isNoContent == true -> {
        ClassName("kotlin", "String") // Return success message
      }

      successResponse?.isArray == true -> {
        val baseType = if (successResponse.ref != null) {
          ClassName(domainPackage, typeMapper.toPascalCase(successResponse.ref))
        } else {
          val mappedType = typeMapper.mapType(successResponse.arrayItemType, successResponse.arrayItemFormat)
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

  private fun buildClientCall(path: PathModel, methodName: String): String {
    val hasBody = path.requestBody != null

    // Map parameter names to safe identifiers
    val params = path.parameters.joinToString(", ") { param ->
      val paramName = typeMapper.toSafeIdentifier(param.name)
      paramName
    }

    return if (hasBody) {
      val callParams = if (params.isNotEmpty()) "$params, body" else "body"
      "return client.$methodName($callParams)"
    } else {
      "return client.$methodName($params)"
    }
  }

  private fun buildMethodKdoc(path: PathModel): String {
    return buildString {
      if (path.summary != null) {
        appendLine(path.summary)
      }
      if (path.description != null && path.description != path.summary) {
        if (path.summary != null) appendLine()
        appendLine(path.description)
      }
      appendLine()
      appendLine("Endpoint: ${path.method.uppercase()} ${path.path}")
      if (path.tags.isNotEmpty()) {
        appendLine()
        appendLine("Tags: ${path.tags.joinToString()}")
      }
    }.trimIndent()
  }

  private fun buildToolClassKdoc(info: ApiInfo, pathCount: Int): String {
    return buildString {
      appendLine("MCP Tool class for: ${info.title}")
      appendLine()
      appendLine("Version: ${info.version}")
      if (info.description != null) {
        appendLine()
        appendLine(info.description)
      }
      appendLine()
      appendLine("This class provides AI/LLM tools that wrap the REST client calls.")
      appendLine("Each method is annotated with @Tool and can be invoked by an AI agent.")
      appendLine()
      appendLine("Available tools: $pathCount")
    }.trimIndent()
  }
}