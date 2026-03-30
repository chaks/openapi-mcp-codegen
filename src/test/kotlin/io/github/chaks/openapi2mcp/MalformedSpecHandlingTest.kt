package io.github.chaks.openapi2mcp.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive tests for handling malformed, hostile, and edge-case OpenAPI specifications.
 *
 * These tests verify that the code generator:
 * - Applies safe defaults for missing fields
 * - Handles null schemas gracefully
 * - Detects and handles circular references
 * - Recovers from broken references
 * - Handles unknown types and formats
 * - Processes non-standard extensions
 */
@DisplayName("Malformed Spec Handling Tests")
class MalformedSpecHandlingTest {

  private lateinit var specNormalizer: SpecNormalizer
  private lateinit var schemaExtractor: SchemaExtractor

  @BeforeEach
  fun setUp() {
    specNormalizer = SpecNormalizer()
    schemaExtractor = SchemaExtractor()
  }

  @Nested
  @DisplayName("Missing Required Fields")
  inner class MissingFieldsTest {

    @Test
    @DisplayName("Should handle missing info section")
    fun testMissingInfoSection() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.isValid, "Should be valid with auto-corrected info")
      assertNotNull(openAPI.info, "Info should be created")
      assertEquals("Unnamed API", openAPI.info?.title)
      assertEquals("1.0.0", openAPI.info?.version)
    }

    @Test
    @DisplayName("Should handle empty API title")
    fun testEmptyTitle() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = ""
          version = "1.0.0"
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.isValid)
      assertEquals("Unnamed API", openAPI.info.title)
    }

    @Test
    @DisplayName("Should handle missing version")
    fun testMissingVersion() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = ""
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.isValid)
      assertEquals("1.0.0", openAPI.info.version)
    }

    @Test
    @DisplayName("Should handle missing components section")
    fun testMissingComponents() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.isValid)
      assertNotNull(openAPI.components, "Components should be created")
    }

    @Test
    @DisplayName("Should handle missing paths section")
    fun testMissingPaths() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.isValid)
      assertNotNull(openAPI.paths, "Paths should be created")
    }
  }

  @Nested
  @DisplayName("Null Schema Handling")
  inner class NullSchemaTest {

    @Test
    @DisplayName("Should handle null schema in components")
    fun testNullSchemaInComponents() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mutableMapOf<String, Schema<*>>()
        }
      }
      // Add null schema - note: Swagger library may not allow null values
      // This tests the extractor's handling
      openAPI.components?.schemas?.put("NullSchema", null)

      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      // The extractor should create a safe default for null schemas
      assertNotNull(schemas["NullSchema"], "Should create safe default for null schema")
      assertEquals("object", schemas["NullSchema"]?.type)
    }
  }

  @Nested
  @DisplayName("Missing Type Field")
  inner class MissingTypeTest {

    @Test
    @DisplayName("Should infer object type from properties")
    fun testInferObjectTypeFromProperties() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "NoTypeSchema" to Schema<Any>().apply {
              properties = mapOf("name" to Schema<String>().apply { type = "string" })
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      assertTrue(result.warnings.any { it.contains("missing 'type'") })
      assertEquals("object", schemas["NoTypeSchema"]?.type)
    }

    @Test
    @DisplayName("Should infer array type from items")
    fun testInferArrayTypeFromItems() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "NoTypeArraySchema" to Schema<Any>().apply {
              items = Schema<String>().apply { type = "string" }
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      assertTrue(result.warnings.any { it.contains("missing 'type'") })
      assertEquals("array", schemas["NoTypeArraySchema"]?.type)
    }

    @Test
    @DisplayName("Should default to object when type cannot be inferred")
    fun testDefaultToObjectWhenTypeUnknown() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "EmptySchema" to Schema<Any>()
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      assertTrue(result.warnings.any { it.contains("missing 'type'") })
      assertEquals("object", schemas["EmptySchema"]?.type)
    }
  }

  @Nested
  @DisplayName("Empty Types Array")
  inner class EmptyTypesArrayTest {

    @Test
    @DisplayName("Should handle empty types array")
    fun testEmptyTypesArray() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "EmptyTypesSchema" to Schema<Any>().apply {
              types = emptySet()
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      // The normalizer should warn about missing type and default to object
      assertTrue(result.warnings.any { it.contains("missing 'type'") || it.contains("empty") })
      assertEquals("object", schemas["EmptyTypesSchema"]?.type)
    }
  }

  @Nested
  @DisplayName("Circular Reference Detection")
  inner class CircularReferenceTest {

    @Test
    @DisplayName("Should detect direct circular reference")
    fun testDirectCircularReference() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "SelfRef" to Schema<Any>().apply {
              type = "object"
              properties = mapOf(
                "self" to Schema<Any>().apply {
                  `$ref` = "#/components/schemas/SelfRef"
                }
              )
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.warnings.any { it.contains("Circular reference") })
    }

    @Test
    @DisplayName("Should detect indirect circular reference")
    fun testIndirectCircularReference() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "SchemaA" to Schema<Any>().apply {
              type = "object"
              properties = mapOf(
                "b" to Schema<Any>().apply {
                  `$ref` = "#/components/schemas/SchemaB"
                }
              )
            },
            "SchemaB" to Schema<Any>().apply {
              type = "object"
              properties = mapOf(
                "a" to Schema<Any>().apply {
                  `$ref` = "#/components/schemas/SchemaA"
                }
              )
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.warnings.any { it.contains("Circular reference") })
    }

    @Test
    @DisplayName("Should handle circular reference during extraction")
    fun testCircularReferenceDuringExtraction() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "Node" to Schema<Any>().apply {
              type = "object"
              properties = mapOf(
                "next" to Schema<Any>().apply {
                  `$ref` = "#/components/schemas/Node"
                }
              )
            }
          )
        }
      }

      schemaExtractor.setCurrentOpenAPI(openAPI)
      val schemas = schemaExtractor.extractSchemas(openAPI)

      // Should not throw, should produce safe default for circular ref
      assertNotNull(schemas["Node"])
    }
  }

  @Nested
  @DisplayName("Broken Reference Handling")
  inner class BrokenReferenceTest {

    @Test
    @DisplayName("Should detect broken reference")
    fun testBrokenReference() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "Referrer" to Schema<Any>().apply {
              type = "object"
              properties = mapOf(
                "missing" to Schema<Any>().apply {
                  `$ref` = "#/components/schemas/NonExistent"
                }
              )
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.errors.any { it.contains("broken reference") })
    }
  }

  @Nested
  @DisplayName("Unknown Type Handling")
  inner class UnknownTypeTest {

    @Test
    @DisplayName("Should handle unknown type")
    fun testUnknownType() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "UnknownType" to Schema<Any>().apply {
              type = "invalid_type_xyz"
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)

      assertTrue(result.warnings.any { it.contains("unknown type") })
    }
  }

  @Nested
  @DisplayName("Composition Handling")
  inner class CompositionHandlingTest {

    @Test
    @DisplayName("Should handle empty oneOf")
    fun testEmptyOneOf() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "EmptyOneOf" to Schema<Any>().apply {
              oneOf = emptyList()
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      assertTrue(result.isValid)
    }

    @Test
    @DisplayName("Should handle empty allOf")
    fun testEmptyAllOf() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "EmptyAllOf" to Schema<Any>().apply {
              allOf = emptyList()
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      assertTrue(result.isValid)
    }

    @Test
    @DisplayName("Should handle empty anyOf")
    fun testEmptyAnyOf() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "EmptyAnyOf" to Schema<Any>().apply {
              anyOf = emptyList()
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      assertTrue(result.isValid)
    }

    @Test
    @DisplayName("Should handle valid oneOf composition")
    fun testValidOneOf() {
      val openAPI = OpenAPI().apply {
        openapi = "3.0.0"
        info = Info().apply {
          title = "Test API"
          version = "1.0.0"
        }
        components = Components().apply {
          schemas = mapOf(
            "TypeA" to Schema<String>().apply { type = "string" },
            "TypeB" to Schema<Int>().apply { type = "integer" },
            "OneOfType" to Schema<Any>().apply {
              oneOf = listOf(
                Schema<String>().apply {
                  `$ref` = "#/components/schemas/TypeA"
                },
                Schema<Int>().apply {
                  `$ref` = "#/components/schemas/TypeB"
                }
              )
            }
          )
        }
      }

      val result = specNormalizer.normalize(openAPI)
      assertTrue(result.isValid)
    }
  }

  @Nested
  @DisplayName("TypeMapper Edge Cases")
  inner class TypeMapperEdgeCasesTest {

    private lateinit var typeMapper: io.github.chaks.openapi2mcp.util.TypeMapper

    @BeforeEach
    fun setUp() {
      typeMapper = io.github.chaks.openapi2mcp.util.TypeMapper()
    }

    @Test
    @DisplayName("Should handle null type")
    fun testNullType() {
      val result = typeMapper.mapType(null, null)
      assertEquals("Any", result)
    }

    @Test
    @DisplayName("Should handle empty type")
    fun testEmptyType() {
      val result = typeMapper.mapType("", null)
      assertEquals("Any", result)
    }

    @Test
    @DisplayName("Should handle null input to toPascalCase")
    fun testNullToPascalCase() {
      val result = typeMapper.toPascalCase(null)
      assertEquals("Any", result)
    }

    @Test
    @DisplayName("Should handle empty input to toPascalCase")
    fun testEmptyToPascalCase() {
      val result = typeMapper.toPascalCase("")
      assertEquals("Any", result)
    }

    @Test
    @DisplayName("Should handle keyword in toSafeIdentifier")
    fun testKeywordToSafeIdentifier() {
      val result = typeMapper.toSafeIdentifier("class")
      assertEquals("`class`", result)
    }

    @Test
    @DisplayName("Should handle null input to toSafeIdentifier")
    fun testNullToSafeIdentifier() {
      val result = typeMapper.toSafeIdentifier(null)
      assertEquals("value", result)
    }

    @Test
    @DisplayName("Should handle input starting with number")
    fun testNumberPrefixToPascalCase() {
      val result = typeMapper.toPascalCase("123name")
      assertTrue(result.startsWith("_"))
    }
  }

  @Nested
  @DisplayName("TypeResolver Edge Cases")
  inner class TypeResolverEdgeCasesTest {

    private lateinit var typeMapper: io.github.chaks.openapi2mcp.util.TypeMapper
    private lateinit var typeResolver: io.github.chaks.openapi2mcp.generator.TypeResolver

    @BeforeEach
    fun setUp() {
      typeMapper = io.github.chaks.openapi2mcp.util.TypeMapper()
      typeResolver = io.github.chaks.openapi2mcp.generator.TypeResolver()
      // Inject typeMapper using reflection or direct assignment
      val field = typeResolver.javaClass.getDeclaredField("typeMapper")
      field.isAccessible = true
      field.set(typeResolver, typeMapper)
    }

    @Test
    @DisplayName("Should handle unknown type in mapBasicType")
    fun testUnknownTypeMapping() {
      val result = typeMapper.mapType("unknown_xyz", null)
      assertEquals("Any", result)
    }
  }
}
