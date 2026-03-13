package io.github.chaks.openapi2mcp.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.chaks.openapi2mcp.cli.CliOptions
import io.github.chaks.openapi2mcp.parser.model.SchemaModel
import io.github.chaks.openapi2mcp.util.TypeMapper
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
      // Remove explicit 'public' modifiers (redundant in Kotlin)
      outputPath.writeText(fileSpec.toString().replace("public ", ""))
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

    // Check if this is a polymorphic schema
    val isPolymorphic = typeMapper.isPolymorphic(schema.oneOf, schema.allOf, schema.anyOf)

    return if (isPolymorphic) {
      generatePolymorphicDomainClass(packageName, schema)
    } else {
      generateRegularDomainClass(packageName, schema)
    }
  }

  private fun generateRegularDomainClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    // Handle enum schemas (no properties but has enum values)
    if (schema.enum != null && schema.properties.isEmpty()) {
      return generateEnumClass(packageName, schema)
    }

    // Handle map-like schemas (only additionalProperties, no properties)
    if (schema.properties.isEmpty() && schema.additionalProperties != null) {
      return generateMapTypeAlias(packageName, schema)
    }

    // Handle empty schemas - generate a simple object, not a data class
    if (schema.properties.isEmpty() && schema.additionalProperties == null) {
      return generateEmptyClass(packageName, schema)
    }

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

  private fun generateMapTypeAlias(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    // Determine the value type for the map
    val (valueType, valueTypeStr) = if (schema.additionalProperties?.ref != null) {
      val cn = ClassName(packageName, typeMapper.toPascalCase(schema.additionalProperties.ref!!))
      Pair(cn, cn.simpleName)
    } else if (schema.additionalProperties?.arrayItemRef != null) {
      val elementType = ClassName(packageName, typeMapper.toPascalCase(schema.additionalProperties.arrayItemRef!!))
      val cn = ClassName("kotlin.collections", "List").parameterizedBy(elementType)
      Pair(cn, "List<${elementType.simpleName}>")
    } else {
      val mappedType = typeMapper.mapType(
        schema.additionalProperties?.type,
        schema.additionalProperties?.format
      )
      val cn = when (mappedType) {
        "String" -> ClassName("kotlin", "String")
        "Int" -> ClassName("kotlin", "Int")
        "Long" -> ClassName("kotlin", "Long")
        "Float" -> ClassName("kotlin", "Float")
        "Double" -> ClassName("kotlin", "Double")
        "Boolean" -> ClassName("kotlin", "Boolean")
        else -> ClassName("kotlin", "Any")
      }
      Pair(cn, mappedType)
    }

    val mapType = ClassName("kotlin.collections", "Map")
      .parameterizedBy(ClassName("kotlin", "String"), valueType)

    val kdoc = buildString {
      appendLine("Type alias for Map<String, $valueTypeStr>")
      appendLine()
      appendLine("Generated from OpenAPI schema: ${schema.name}")
      if (schema.description != null) {
        appendLine()
        appendLine(schema.description)
      }
    }

    val typeAliasSpec = TypeAliasSpec.builder(className, mapType)
      .addKdoc(kdoc.trimIndent())
      .build()

    return FileSpec.builder(packageName, schema.name)
      .addTypeAlias(typeAliasSpec)
      .indent("    ")
      .build()
  }

  private fun generateEmptyClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    val classBuilder = TypeSpec.classBuilder(className)
      .addKdoc(generateClassKdoc(schema))

    return FileSpec.builder(packageName, schema.name)
      .addType(classBuilder.build())
      .indent("    ")
      .build()
  }

  private fun generateEnumClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    val enumBuilder = TypeSpec.enumBuilder(className)
      .addKdoc(generateClassKdoc(schema))

    // Add enum values
    schema.enum?.forEach { enumValue ->
      val enumName = typeMapper.toPascalCase(enumValue)
      enumBuilder.addEnumConstant(enumName)
    }

    return FileSpec.builder(packageName, schema.name)
      .addType(enumBuilder.build())
      .indent("    ")
      .build()
  }

  private fun generatePolymorphicDomainClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)

    // Handle oneOf/anyOf - generate sealed class with subclasses
    if (!schema.oneOf.isNullOrEmpty() || !schema.anyOf.isNullOrEmpty()) {
      return generateSealedDomainClass(packageName, schema)
    }

    // Handle allOf - generate combined class
    if (!schema.allOf.isNullOrEmpty()) {
      return generateAllOfDomainClass(packageName, schema)
    }

    // Fallback to regular class
    return generateRegularDomainClass(packageName, schema)
  }

  private fun generateSealedDomainClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)
    val refs = schema.oneOf ?: schema.anyOf ?: emptyList()

    val sealedClassBuilder = TypeSpec.classBuilder(className)
      .addModifiers(KModifier.SEALED)
      .addKdoc(buildString {
        appendLine("Generated sealed class from OpenAPI schema: ${schema.name}")
        if (schema.description != null) {
          appendLine()
          appendLine(schema.description)
        }
        appendLine()
        appendLine("Polymorphic type that can be one of:")
        refs.forEach { ref ->
          appendLine("- $ref")
        }
      })

    // Add Jackson @JsonTypeInfo annotation for polymorphic serialization
    sealedClassBuilder.addAnnotation(
      AnnotationSpec.builder(
        ClassName("com.fasterxml.jackson.annotation", "JsonTypeInfo")
      )
        .addMember("use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME")
        .addMember("include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY")
        .addMember("property = %S", "type")
        .build()
    )

    val fileSpecBuilder = FileSpec.builder(packageName, schema.name)

    // Add subclasses for each reference with "Variant" suffix to avoid conflicts
    refs.forEach { ref ->
      val refClassName = typeMapper.toPascalCase(ref)
      val subclassName = "${refClassName}Variant"
      val subclassBuilder = TypeSpec.classBuilder(subclassName)
        .addModifiers(KModifier.DATA)
        .superclass(ClassName(packageName, className))
        .addKdoc("Represents a $ref in the polymorphic $className type.")

      // Add Jackson @JsonTypeName annotation
      subclassBuilder.addAnnotation(
        AnnotationSpec.builder(
          ClassName("com.fasterxml.jackson.annotation", "JsonTypeName")
        ).addMember("%S", ref).build()
      )

      // Add a value property that references the actual schema type
      val valueProperty = PropertySpec.builder(
        "value",
        ClassName(packageName, refClassName)
      )
        .addKdoc("The underlying $ref value")
        .initializer("value")
        .build()

      subclassBuilder.addProperty(valueProperty)

      val constructorBuilder = FunSpec.constructorBuilder()
        .addParameter("value", ClassName(packageName, refClassName))
      subclassBuilder.primaryConstructor(constructorBuilder.build())

      fileSpecBuilder.addType(subclassBuilder.build())
    }

    fileSpecBuilder.addType(sealedClassBuilder.build())
      .addImport("com.fasterxml.jackson.annotation", "JsonTypeInfo")
      .addImport("com.fasterxml.jackson.annotation", "JsonTypeName")

    return fileSpecBuilder
      .indent("    ")
      .build()
  }

  private fun generateAllOfDomainClass(packageName: String, schema: SchemaModel): FileSpec {
    val className = typeMapper.toPascalCase(schema.name)
    val refs = schema.allOf ?: emptyList()

    val classBuilder = TypeSpec.classBuilder(className)
      .addModifiers(KModifier.DATA)
      .addKdoc(buildString {
        appendLine("Generated data class from OpenAPI schema: ${schema.name}")
        if (schema.description != null) {
          appendLine()
          appendLine(schema.description)
        }
        appendLine()
        appendLine("Combines properties from:")
        refs.forEach { ref ->
          appendLine("- $ref")
        }
      })

    // Add Jackson annotations
    classBuilder.addAnnotation(
      ClassName("com.fasterxml.jackson.databind.annotation", "JsonSerialize")
    )
    classBuilder.addAnnotation(
      ClassName("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
    )

    // For allOf, we'd need to combine properties from all referenced schemas
    // This is a simplified version - in production, you'd merge properties
    val constructorBuilder = FunSpec.constructorBuilder()

    refs.forEach { ref ->
      val propRefName = typeMapper.toCamelCase(ref)
      val propRefType = ClassName(packageName, typeMapper.toPascalCase(ref))

      val propertyBuilder = PropertySpec.builder(propRefName, propRefType)
        .addKdoc("Property from $ref")
        .initializer(propRefName)
        .build()

      classBuilder.addProperty(propertyBuilder)

      val parameterBuilder = ParameterSpec.builder(propRefName, propRefType)
      constructorBuilder.addParameter(parameterBuilder.build())
    }

    classBuilder.primaryConstructor(constructorBuilder.build())

    return FileSpec.builder(packageName, schema.name)
      .addType(classBuilder.build())
      .addImport("com.fasterxml.jackson.databind.annotation", "JsonSerialize")
      .addImport("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
      .indent("    ")
      .build()
  }

  private fun determinePropertyType(
    propInfo: io.github.chaks.openapi2mcp.parser.model.PropertyInfo,
    domainPackage: String
  ): com.squareup.kotlinpoet.TypeName {
    return when {
      typeMapper.isPolymorphic(propInfo.oneOf, propInfo.allOf, propInfo.anyOf) -> {
        // Use the generated polymorphic type name
        ClassName(domainPackage, typeMapper.mapPolymorphicType(propInfo.oneOf, propInfo.allOf, propInfo.anyOf) ?: "Any")
      }
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
        // Add polymorphic references
        propInfo.oneOf?.forEach { allRefs.add(it) }
        propInfo.allOf?.forEach { allRefs.add(it) }
        propInfo.anyOf?.forEach { allRefs.add(it) }
      }
      // Add schema-level polymorphic references
      schema.oneOf?.forEach { allRefs.add(it) }
      schema.allOf?.forEach { allRefs.add(it) }
      schema.anyOf?.forEach { allRefs.add(it) }
    }

    // Generate any referenced schemas that are in our schemas map
    allRefs.forEach { ref ->
      schemas[ref]?.let { schema ->
        val domainPackage = options.domainPackage
        val domainPath = getDomainPath(options.output, domainPackage)
        val fileSpec = generateDomainClass(domainPackage, schema)
        val outputPath = domainPath.resolve("${schema.name}.kt")
        // Remove explicit 'public' modifiers (redundant in Kotlin)
        outputPath.writeText(fileSpec.toString().replace("public ", ""))
      }
    }
  }

  /**
   * Helper class for building annotations with KotlinPoet.
   */

}