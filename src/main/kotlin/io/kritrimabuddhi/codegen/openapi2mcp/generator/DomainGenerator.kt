package io.kritrimabuddhi.codegen.openapi2mcp.generator

import io.kritrimabuddhi.codegen.openapi2mcp.cli.CliOptions
import io.kritrimabuddhi.codegen.openapi2mcp.parser.model.SchemaModel
import io.kritrimabuddhi.codegen.openapi2mcp.util.TypeMapper
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates domain data classes from OpenAPI schema definitions.
 *
 * Uses KotlinPoet to generate type-safe Kotlin data classes with
 * appropriate Jackson/JSON-B annotations for serialization.
 */
@ApplicationScoped
class DomainGenerator {

  @Inject
  private lateinit var typeMapper: TypeMapper

  /**
   * Generate domain classes from schemas.
   *
   * @param options CLI options containing output directory and package info
   * @param schemas Map of schema names to SchemaModel
   */
  fun generate(options: CliOptions, schemas: Map<String, SchemaModel>) {
    val domainPackage = options.domainPackage
    val domainPath = getDomainPath(options.output, domainPackage)

    if (!java.nio.file.Files.exists(domainPath)) {
      domainPath.createDirectories()
    }

    schemas.forEach { (name, schema) ->
      val fileSpec = generateDomainClass(domainPackage, schema)
      val outputPath = domainPath.resolve("${schema.name}.kt")
      outputPath.writeText(fileSpec.toString())
    }

    // Generate nested schemas first (dependencies)
    generateNestedSchemas(options, schemas)
  }

  private fun getDomainPath(outputDir: Path, domainPackage: String): Path {
    val packagePath = domainPackage.replace('.', '/')
    return outputDir.resolve("src/main/kotlin").resolve(packagePath)
  }

  private fun generateDomainClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    val classBuilder = TypeSpec.classBuilder(className)
      .addModifiers(KModifier.DATA)
      .addKdoc(generateClassKdoc(schema))

    // Add Jackson annotations for JSON serialization
    classBuilder.addAnnotation(
      ClassName("com.fasterxml.jackson.databind.annotation", "JsonSerialize")
    )
    classBuilder.addAnnotation(
      ClassName("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
    )

    // Generate constructor parameters and properties
    val constructorBuilder = FunSpec.constructorBuilder()

    schema.properties.forEach { (propName, propInfo) ->
      val safeName = typeMapper.toSafeIdentifier(propName)
      val isNullable = propInfo.isNullable || schema.required.contains(propName).not()
      val type = determinePropertyType(propInfo, packageName).copy(nullable = isNullable)

      val propertyBuilder = PropertySpec.builder(safeName, type)
        .addKdoc(propInfo.description ?: "Property: $propName")
        .initializer(safeName)

      // Add annotations
      propertyBuilder.addAnnotation(
        com.squareup.kotlinpoet.AnnotationSpec.builder(
          ClassName("com.fasterxml.jackson.annotation", "JsonProperty")
        )
          .addMember("%S", propName)
          .build()
      )

      if (propInfo.enum != null) {
        // For enum properties, we might want to add validation
        propertyBuilder.addKdoc("\nAllowed values: ${propInfo.enum.joinToString()}")
      }

      if (propInfo.defaultValue != null) {
        propertyBuilder.addKdoc("\nDefault value: ${propInfo.defaultValue}")
      }


      classBuilder.addProperty(propertyBuilder.build())

      val parameterBuilder = ParameterSpec.builder(safeName, type)
      if (isNullable) {
        parameterBuilder.defaultValue("null")
      }
      constructorBuilder.addParameter(parameterBuilder.build())
    }

    classBuilder.primaryConstructor(constructorBuilder.build())

    // Add toString, equals, hashCode for data classes are auto-generated

    return FileSpec.builder(packageName, schema.name)
      .addType(classBuilder.build())
      .addImport("com.fasterxml.jackson.databind.annotation", "JsonSerialize")
      .addImport("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
      .indent("    ")
      .build()
  }

  private fun determinePropertyType(
    propInfo: io.kritrimabuddhi.codegen.openapi2mcp.parser.model.PropertyInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      propInfo.isArray -> {
        val elementType = if (propInfo.arrayItemRef != null) {
          ClassName(domainPackage, typeMapper.toPascalCase(propInfo.arrayItemRef))
        } else {
          val mappedType = typeMapper.mapType(propInfo.arrayItemType, propInfo.arrayItemFormat)
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

      propInfo.ref != null -> {
        ClassName(domainPackage, typeMapper.toPascalCase(propInfo.ref))
      }

      else -> {
        val mappedType = typeMapper.mapType(propInfo.type, propInfo.format)
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

  private fun generateClassKdoc(schema: SchemaModel): String {
    val kdoc = buildString {
      appendLine("Generated data class from OpenAPI schema: ${schema.name}")
      if (schema.description != null) {
        appendLine()
        appendLine(schema.description)
      }
      if (schema.enum != null) {
        appendLine()
        appendLine("Enum values: ${schema.enum.joinToString()}")
      }
    }
    return kdoc.trimIndent()
  }

  private fun generateNestedSchemas(options: CliOptions, schemas: Map<String, SchemaModel>) {
    // Find all referenced schemas that need to be generated
    val allRefs = mutableSetOf<String>()

    schemas.forEach { (_, schema) ->
      schema.properties.forEach { (_, propInfo) ->
        if (propInfo.ref != null) {
          allRefs.add(propInfo.ref)
        }
        if (propInfo.arrayItemRef != null) {
          allRefs.add(propInfo.arrayItemRef)
        }
      }
    }

    // Generate any referenced schemas that are in our schemas map
    allRefs.forEach { ref ->
      schemas[ref]?.let { schema ->
        val domainPackage = options.domainPackage
        val domainPath = getDomainPath(options.output, domainPackage)
        val fileSpec = generateDomainClass(domainPackage, schema)
        val outputPath = domainPath.resolve("${schema.name}.kt")
        outputPath.writeText(fileSpec.toString())
      }
    }
  }

  /**
   * Helper class for building annotations with KotlinPoet.
   */

}