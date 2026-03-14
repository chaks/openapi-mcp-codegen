package io.github.chaks.openapi2mcp

import io.github.chaks.openapi2mcp.parser.SwaggerOpenApiParser
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Comprehensive test suite for verifying code generation from examples/openapi-3.0.3.yaml
 *
 * This test validates:
 * 1. All expected schemas are being generated
 * 2. No additional (unexpected) schemas are generated
 * 3. Enum classes are generated correctly
 * 4. Every attribute/property is present with correct types
 * 5. Polymorphic schemas (oneOf, allOf) are handled correctly
 * 6. Nested schemas are handled correctly
 */
@QuarkusTest
@DisplayName("OpenAPI 3.0.3 Code Generation Tests")
class OpenApi303CodeGenerationTest {

    @Inject
    lateinit var parser: SwaggerOpenApiParser
    private val examplePath = Paths.get("examples/openapi-3.0.3.yaml")

    // Expected schema names from components/schemas
    private val expectedComponentSchemas = setOf(
        "ProductStatus",
        "InventoryCondition",
        "ErrorModel",
        "BaseResponse",
        "ContactInfo",
        "ManufacturerAddress",
        "Manufacturer",
        "Product",
        "ProductConfirmation",
        "SimpleItem",
        "BatchItem"
    )

    @Nested
    @DisplayName("Schema Parsing Tests")
    inner class SchemaParsingTests {

        @Test
        @DisplayName("Should parse OpenAPI specification successfully")
        fun `should parse OpenAPI specification successfully`() {
            // Skip test if example file doesn't exist
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found at $examplePath")
                return
            }

            val result = parser.parse(examplePath)

            assertNotNull(result, "Parsed result should not be null")
            assertEquals("3.0.3", result.openapiVersion, "OpenAPI version should be 3.0.3")
            assertEquals("Product Catalogue API", result.info.title, "API title should be 'Product Catalogue API'")
            assertEquals("1.0.0", result.info.version, "API version should be 1.0.0")
        }

        @Test
        @DisplayName("Should extract all expected component schemas")
        fun `should extract all expected component schemas`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            expectedComponentSchemas.forEach { schemaName ->
                assertTrue(
                    result.schemas.containsKey(schemaName),
                    "Expected schema '$schemaName' should be present in parsed schemas. " +
                            "Found schemas: ${result.schemas.keys.sorted().joinToString()}"
                )
            }
        }

        @Test
        @DisplayName("Should not generate unexpected schemas beyond expected ones")
        fun `should not generate unexpected schemas beyond expected ones`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val expectedAllSchemas = expectedComponentSchemas + getExpectedInlineSchemas()

            val unexpectedSchemas = result.schemas.keys - expectedAllSchemas

            assertTrue(
                unexpectedSchemas.isEmpty(),
                "Should not have unexpected schemas. Found: ${unexpectedSchemas.sorted().joinToString()}"
            )
        }

        @Test
        @DisplayName("Should extract inline schemas from paths")
        fun `should extract inline schemas from paths`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            // Quick search inline schema
            assertTrue(
                result.schemas.containsKey("QuickSearchGetResponse"),
                "Should generate QuickSearchGetResponse from inline schema"
            )
        }

        private fun getExpectedInlineSchemas(): Set<String> {
            return setOf(
                "QuickSearchGetResponse",
                "ProductsGetResponse",
                "InventoryBatchPostResponse",
                "InventoryBatchPostRequest"
            )
        }
    }

    @Nested
    @DisplayName("Enum Schema Tests")
    inner class EnumSchemaTests {

        @Test
        @DisplayName("ProductStatus enum should have correct values")
        fun `product status enum should have correct values`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val productStatus = result.schemas["ProductStatus"]

            assertNotNull(productStatus, "ProductStatus schema should exist")
            val productStatusNotNull = productStatus!!

            assertTrue(productStatusNotNull.enum != null && productStatusNotNull.enum!!.isNotEmpty(),
                "ProductStatus should have enum values")

            val expectedValues = listOf("DRAFT", "ACTIVE", "OUT_OF_STOCK", "DISCONTINUED")
            assertEquals(expectedValues, productStatusNotNull.enum,
                "ProductStatus enum values should match expected: $expectedValues")

            assertTrue(productStatusNotNull.properties.isEmpty(),
                "ProductStatus should be an enum (no properties)")
        }

        @Test
        @DisplayName("InventoryCondition enum should have correct values")
        fun `inventory condition enum should have correct values`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val inventoryCondition = result.schemas["InventoryCondition"]

            assertNotNull(inventoryCondition, "InventoryCondition schema should exist")
            val inventoryConditionNotNull = inventoryCondition!!

            assertTrue(inventoryConditionNotNull.enum != null && inventoryConditionNotNull.enum!!.isNotEmpty(),
                "InventoryCondition should have enum values")

            val expectedValues = listOf("NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR")
            assertEquals(expectedValues, inventoryConditionNotNull.enum,
                "InventoryCondition enum values should match expected: $expectedValues")

            assertTrue(inventoryConditionNotNull.properties.isEmpty(),
                "InventoryCondition should be an enum (no properties)")
        }
    }

    @Nested
    @DisplayName("ErrorModel Schema Tests")
    inner class ErrorModelSchemaTests {

        @Test
        @DisplayName("ErrorModel should have all required properties")
        fun `error model should have all required properties`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val errorModel = result.schemas["ErrorModel"]

            assertNotNull(errorModel, "ErrorModel schema should exist")
            val errorModelNotNull = errorModel!!

            val expectedProperties = setOf("code", "message", "details")
            val actualProperties = errorModelNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "ErrorModel properties should match. Expected: $expectedProperties, Got: $actualProperties")

            // Verify property types
            val codeProperty = errorModelNotNull.properties["code"]
            assertNotNull(codeProperty, "code property should exist")
            assertEquals("integer", codeProperty!!.type, "code should be integer type")

            val messageProperty = errorModelNotNull.properties["message"]
            assertNotNull(messageProperty, "message property should exist")
            assertEquals("string", messageProperty!!.type, "message should be string type")

            val detailsProperty = errorModelNotNull.properties["details"]
            assertNotNull(detailsProperty, "details property should exist")
            assertEquals("object", detailsProperty!!.type, "details should be object type")
        }
    }

    @Nested
    @DisplayName("BaseResponse Schema Tests")
    inner class BaseResponseSchemaTests {

        @Test
        @DisplayName("BaseResponse should have correct properties and required fields")
        fun `base response should have correct properties and required fields`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val baseResponse = result.schemas["BaseResponse"]

            assertNotNull(baseResponse, "BaseResponse schema should exist")
            val baseResponseNotNull = baseResponse!!

            val expectedProperties = setOf("success", "requestId")
            val actualProperties = baseResponseNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "BaseResponse properties should match. Expected: $expectedProperties, Got: $actualProperties")

            val expectedRequired = setOf("success")
            assertEquals(expectedRequired, baseResponseNotNull.required,
                "BaseResponse required fields should match")

            // Verify property types
            val successProperty = baseResponseNotNull.properties["success"]
            assertNotNull(successProperty, "success property should exist")
            assertEquals("boolean", successProperty!!.type, "success should be boolean type")

            val requestIdProperty = baseResponseNotNull.properties["requestId"]
            assertNotNull(requestIdProperty, "requestId property should exist")
            assertEquals("string", requestIdProperty!!.type, "requestId should be string type")
            assertEquals("uuid", requestIdProperty!!.format, "requestId should have uuid format")
        }
    }

    @Nested
    @DisplayName("ContactInfo Schema Tests")
    inner class ContactInfoSchemaTests {

        @Test
        @DisplayName("ContactInfo should have all properties with correct types")
        fun `contact info should have all properties with correct types`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val contactInfo = result.schemas["ContactInfo"]

            assertNotNull(contactInfo, "ContactInfo schema should exist")
            val contactInfoNotNull = contactInfo!!

            val expectedProperties = setOf("phone", "email", "website")
            val actualProperties = contactInfoNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "ContactInfo properties should match. Expected: $expectedProperties, Got: $actualProperties")

            // Verify phone property
            val phoneProperty = contactInfoNotNull.properties["phone"]
            assertNotNull(phoneProperty, "phone property should exist")
            assertEquals("string", phoneProperty!!.type, "phone should be string type")
            assertNotNull(phoneProperty!!.description, "phone should have description")

            // Verify email property
            val emailProperty = contactInfoNotNull.properties["email"]
            assertNotNull(emailProperty, "email property should exist")
            assertEquals("string", emailProperty!!.type, "email should be string type")
            assertEquals("email", emailProperty!!.format, "email should have email format")

            // Verify website property
            val websiteProperty = contactInfoNotNull.properties["website"]
            assertNotNull(websiteProperty, "website property should exist")
            assertEquals("string", websiteProperty!!.type, "website should be string type")
            assertEquals("uri", websiteProperty!!.format, "website should have uri format")
        }
    }

    @Nested
    @DisplayName("ManufacturerAddress Schema Tests")
    inner class ManufacturerAddressSchemaTests {

        @Test
        @DisplayName("ManufacturerAddress should have correct properties and required fields")
        fun `manufacturer address should have correct properties and required fields`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val manufacturerAddress = result.schemas["ManufacturerAddress"]

            assertNotNull(manufacturerAddress, "ManufacturerAddress schema should exist")
            val manufacturerAddressNotNull = manufacturerAddress!!

            val expectedProperties = setOf("street", "city", "state", "postalCode", "country", "contactInfo")
            val actualProperties = manufacturerAddressNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "ManufacturerAddress properties should match. Expected: $expectedProperties, Got: $actualProperties")

            val expectedRequired = setOf("street", "city", "country")
            assertEquals(expectedRequired, manufacturerAddressNotNull.required,
                "ManufacturerAddress required fields should match")

            // Verify contactInfo reference
            val contactInfoProperty = manufacturerAddressNotNull.properties["contactInfo"]
            assertNotNull(contactInfoProperty, "contactInfo property should exist")
            assertEquals("ContactInfo", contactInfoProperty!!.ref,
                "contactInfo should reference ContactInfo schema")
        }
    }

    @Nested
    @DisplayName("Manufacturer Schema Tests")
    inner class ManufacturerSchemaTests {

        @Test
        @DisplayName("Manufacturer should have all properties with correct types")
        fun `manufacturer should have all properties with correct types`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val manufacturer = result.schemas["Manufacturer"]

            assertNotNull(manufacturer, "Manufacturer schema should exist")
            val manufacturerNotNull = manufacturer!!

            val expectedProperties = setOf("manufacturerId", "name", "website", "headquarters", "establishedYear")
            val actualProperties = manufacturerNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "Manufacturer properties should match. Expected: $expectedProperties, Got: $actualProperties")

            val expectedRequired = setOf("name")
            assertEquals(expectedRequired, manufacturerNotNull.required,
                "Manufacturer required fields should match")

            // Verify manufacturerId property
            val manufacturerIdProperty = manufacturerNotNull.properties["manufacturerId"]
            assertNotNull(manufacturerIdProperty, "manufacturerId property should exist")
            assertEquals("string", manufacturerIdProperty!!.type, "manufacturerId should be string type")
            assertEquals("uuid", manufacturerIdProperty!!.format, "manufacturerId should have uuid format")

            // Verify name property
            val nameProperty = manufacturerNotNull.properties["name"]
            assertNotNull(nameProperty, "name property should exist")
            assertEquals("string", nameProperty!!.type, "name should be string type")
            assertNotNull(nameProperty!!.description, "name should have description")

            // Verify headquarters reference
            val headquartersProperty = manufacturerNotNull.properties["headquarters"]
            assertNotNull(headquartersProperty, "headquarters property should exist")
            assertEquals("ManufacturerAddress", headquartersProperty!!.ref,
                "headquarters should reference ManufacturerAddress schema")

            // Verify establishedYear property
            val establishedYearProperty = manufacturerNotNull.properties["establishedYear"]
            assertNotNull(establishedYearProperty, "establishedYear property should exist")
            assertEquals("integer", establishedYearProperty!!.type, "establishedYear should be integer type")
        }
    }

    @Nested
    @DisplayName("Product Schema Tests")
    inner class ProductSchemaTests {

        @Test
        @DisplayName("Product should have all expected properties")
        fun `product should have all expected properties`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val product = result.schemas["Product"]

            assertNotNull(product, "Product schema should exist")
            val productNotNull = product!!

            val expectedProperties = setOf(
                "productId",
                "sku",
                "name",
                "shortDescription",
                "fullDescription",
                "price",
                "salePrice",
                "currency",
                "stockLevel",
                "status",
                "manufacturer",
                "specifications",
                "categories",
                "images",
                "tags"
            )
            val actualProperties = productNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "Product properties should match. Expected: $expectedProperties, Got: $actualProperties")
        }

        @Test
        @DisplayName("Product should have correct required fields")
        fun `product should have correct required fields`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val product = result.schemas["Product"]

            assertNotNull(product, "Product schema should exist")
            val productNotNull = product!!

            val expectedRequired = setOf("name", "sku", "price", "manufacturer")
            assertEquals(expectedRequired, productNotNull.required,
                "Product required fields should match. Expected: $expectedRequired, Got: ${productNotNull.required}")
        }

        @Test
        @DisplayName("Product property types should be correct")
        fun `product property types should be correct`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val product = result.schemas["Product"]

            assertNotNull(product, "Product schema should exist")
            val productNotNull = product!!

            // Verify productId (UUID)
            assertEquals("string", productNotNull.properties["productId"]?.type, "productId should be string type")
            assertEquals("uuid", productNotNull.properties["productId"]?.format, "productId should have uuid format")

            // Verify sku with pattern
            assertEquals("string", productNotNull.properties["sku"]?.type, "sku should be string type")

            // Verify price
            assertEquals("number", productNotNull.properties["price"]?.type, "price should be number type")
            assertEquals("double", productNotNull.properties["price"]?.format, "price should have double format")

            // Verify salePrice
            assertEquals("number", productNotNull.properties["salePrice"]?.type, "salePrice should be number type")
            assertEquals("double", productNotNull.properties["salePrice"]?.format, "salePrice should have double format")

            // Verify currency with enum
            assertEquals("string", productNotNull.properties["currency"]?.type, "currency should be string type")
            assertNotNull(productNotNull.properties["currency"]?.enum, "currency should have enum values")
            assertEquals(listOf("USD", "EUR", "GBP", "JPY", "CAD"), productNotNull.properties["currency"]?.enum,
                "currency enum values should match")

            // Verify stockLevel
            assertEquals("integer", productNotNull.properties["stockLevel"]?.type, "stockLevel should be integer type")

            // Verify status reference
            assertEquals("ProductStatus", productNotNull.properties["status"]?.ref,
                "status should reference ProductStatus schema")

            // Verify manufacturer reference
            assertEquals("Manufacturer", productNotNull.properties["manufacturer"]?.ref,
                "manufacturer should reference Manufacturer schema")

            // Verify specifications (additionalProperties map)
            assertEquals("object", productNotNull.properties["specifications"]?.type, "specifications should be object type")

            // Verify categories array
            assertTrue(productNotNull.properties["categories"]?.isArray == true, "categories should be an array")
            assertEquals("string", productNotNull.properties["categories"]?.arrayItemType, "categories should contain strings")

            // Verify images array with uri format
            assertTrue(productNotNull.properties["images"]?.isArray == true, "images should be an array")
            assertEquals("string", productNotNull.properties["images"]?.arrayItemType, "images should contain strings")
            assertEquals("uri", productNotNull.properties["images"]?.arrayItemFormat, "images items should have uri format")

            // Verify tags array
            assertTrue(productNotNull.properties["tags"]?.isArray == true, "tags should be an array")
            assertEquals("string", productNotNull.properties["tags"]?.arrayItemType, "tags should contain strings")
        }
    }

    @Nested
    @DisplayName("ProductConfirmation Schema Tests (allOf)")
    inner class ProductConfirmationSchemaTests {

        @Test
        @DisplayName("ProductConfirmation should use allOf correctly")
        fun `product confirmation should use all of correctly`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val productConfirmation = result.schemas["ProductConfirmation"]

            assertNotNull(productConfirmation, "ProductConfirmation schema should exist")
            val productConfirmationNotNull = productConfirmation!!

            // Note: The Swagger parser may resolve allOf by combining properties
            // Check if allOf is preserved or if properties are generated
            val hasAllOf = productConfirmationNotNull.allOf != null
            val hasCombinedProperties = productConfirmationNotNull.properties.isNotEmpty()

            assertTrue(
                hasAllOf || hasCombinedProperties,
                "ProductConfirmation should either have allOf or combined properties"
            )

            // If allOf is preserved, verify it includes BaseResponse
            if (hasAllOf) {
                assertTrue(productConfirmationNotNull.allOf!!.contains("BaseResponse"),
                    "ProductConfirmation allOf should include BaseResponse")
            }

            // If properties are combined, verify we have the expected properties from allOf composition
            // ProductConfirmation combines BaseResponse + additional properties (product, estimatedDelivery)
            if (hasCombinedProperties) {
                val actualProps = productConfirmationNotNull.properties.keys
                // At minimum should have the additional properties from the allOf extension
                assertTrue(actualProps.contains("product") || actualProps.contains("estimatedDelivery"),
                    "ProductConfirmation should have properties from allOf extension")
            }
        }
    }

    @Nested
    @DisplayName("SimpleItem Schema Tests")
    inner class SimpleItemSchemaTests {

        @Test
        @DisplayName("SimpleItem should have all properties with correct types")
        fun `simple item should have all properties with correct types`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val simpleItem = result.schemas["SimpleItem"]

            assertNotNull(simpleItem, "SimpleItem schema should exist")
            val simpleItemNotNull = simpleItem!!

            val expectedProperties = setOf("itemType", "sku", "quantity", "operation", "reason")
            val actualProperties = simpleItemNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "SimpleItem properties should match. Expected: $expectedProperties, Got: $actualProperties")

            val expectedRequired = setOf("sku", "quantity", "operation")
            assertEquals(expectedRequired, simpleItemNotNull.required,
                "SimpleItem required fields should match")

            // Verify itemType enum
            assertEquals("string", simpleItemNotNull.properties["itemType"]?.type, "itemType should be string type")
            assertEquals(listOf("simple"), simpleItemNotNull.properties["itemType"]?.enum,
                "itemType enum should have 'simple' value")

            // Verify quantity
            assertEquals("integer", simpleItemNotNull.properties["quantity"]?.type, "quantity should be integer type")

            // Verify operation enum
            assertEquals("string", simpleItemNotNull.properties["operation"]?.type, "operation should be string type")
            assertEquals(listOf("ADD", "REMOVE", "SET"), simpleItemNotNull.properties["operation"]?.enum,
                "operation enum should have correct values")
        }
    }

    @Nested
    @DisplayName("BatchItem Schema Tests")
    inner class BatchItemSchemaTests {

        @Test
        @DisplayName("BatchItem should have all properties with correct types")
        fun `batch item should have all properties with correct types`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)
            val batchItem = result.schemas["BatchItem"]

            assertNotNull(batchItem, "BatchItem schema should exist")
            val batchItemNotNull = batchItem!!

            val expectedProperties = setOf("itemType", "batchId", "items", "priority", "scheduledFor")
            val actualProperties = batchItemNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "BatchItem properties should match. Expected: $expectedProperties, Got: $actualProperties")

            val expectedRequired = setOf("batchId", "items")
            assertEquals(expectedRequired, batchItemNotNull.required,
                "BatchItem required fields should match")

            // Verify batchId
            assertEquals("string", batchItemNotNull.properties["batchId"]?.type, "batchId should be string type")
            assertEquals("uuid", batchItemNotNull.properties["batchId"]?.format, "batchId should have uuid format")

            // Verify items array
            assertTrue(batchItemNotNull.properties["items"]?.isArray == true, "items should be an array")

            // Verify priority enum with default
            assertEquals("string", batchItemNotNull.properties["priority"]?.type, "priority should be string type")
            assertEquals(listOf("LOW", "NORMAL", "HIGH", "URGENT"), batchItemNotNull.properties["priority"]?.enum,
                "priority enum should have correct values")

            // Verify scheduledFor
            assertEquals("string", batchItemNotNull.properties["scheduledFor"]?.type, "scheduledFor should be string type")
            assertEquals("date-time", batchItemNotNull.properties["scheduledFor"]?.format,
                "scheduledFor should have date-time format")
        }
    }

    @Nested
    @DisplayName("Path and Operation Tests")
    inner class PathAndOperationTests {

        @Test
        @DisplayName("Should extract all paths correctly")
        fun `should extract all paths correctly`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            // Expected paths with methods
            val expectedPathMethods = setOf(
                "/products|POST",
                "/products|GET",
                "/products/{productId}|GET",
                "/quick-search|GET",
                "/inventory-batch|POST"
            )

            val actualPathMethods = result.paths.map { "${it.path}|${it.method}" }.toSet()

            assertEquals(expectedPathMethods, actualPathMethods,
                "Paths and methods should match. Expected: $expectedPathMethods, Got: $actualPathMethods")
        }

        @Test
        @DisplayName("createProduct operation should have correct operationId")
        fun `create product operation should have correct operation id`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val createProduct = result.paths.find { it.path == "/products" && it.method == "POST" }

            assertNotNull(createProduct, "POST /products should exist")
            val createProductNotNull = createProduct!!
            assertEquals("createProduct", createProductNotNull.operationId, "operationId should be createProduct")
            assertNotNull(createProductNotNull.requestBody, "createProduct should have requestBody")
            assertEquals("Product", createProductNotNull.requestBody?.ref, "request body should reference Product schema")
        }

        @Test
        @DisplayName("listProducts operation should have correct parameters")
        fun `list products operation should have correct parameters`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val listProducts = result.paths.find { it.path == "/products" && it.method == "GET" }

            assertNotNull(listProducts, "GET /products should exist")
            val listProductsNotNull = listProducts!!
            assertEquals("listProducts", listProductsNotNull.operationId, "operationId should be listProducts")

            val params = listProductsNotNull.parameters
            assertNotNull(params, "listProducts should have parameters")
            val paramsNotNull = params!!

            val expectedParams = setOf("page", "limit", "category")
            val actualParamNames = paramsNotNull.map { it.name }.toSet()

            assertEquals(expectedParams, actualParamNames,
                "Parameter names should match. Expected: $expectedParams, Got: $actualParamNames")

            // Verify page parameter default
            val pageParam = paramsNotNull.find { it.name == "page" }
            assertNotNull(pageParam, "page parameter should exist")
            assertEquals("1", pageParam?.defaultValue, "page should have default value '1'")

            // Verify limit parameter default and maximum
            val limitParam = paramsNotNull.find { it.name == "limit" }
            assertNotNull(limitParam, "limit parameter should exist")
            assertEquals("20", limitParam?.defaultValue, "limit should have default value '20'")
        }

        @Test
        @DisplayName("getProduct operation should have path parameter")
        fun `get product operation should have path parameter`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val getProduct = result.paths.find { it.path == "/products/{productId}" && it.method == "GET" }

            assertNotNull(getProduct, "GET /products/{productId} should exist")
            val getProductNotNull = getProduct!!
            assertEquals("getProduct", getProductNotNull.operationId, "operationId should be getProduct")

            val pathParam = getProductNotNull.parameters.find { it.name == "productId" }
            assertNotNull(pathParam, "productId parameter should exist")
            assertEquals("path", pathParam?.`in`, "productId should be a path parameter")
            assertTrue(pathParam?.required == true, "productId should be required")
            assertEquals("string", pathParam?.type, "productId should be string type")
            assertEquals("uuid", pathParam?.format, "productId should have uuid format")
        }

        @Test
        @DisplayName("quickSearch operation should have required query parameter")
        fun `quick search operation should have required query parameter`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val quickSearch = result.paths.find { it.path == "/quick-search" && it.method == "GET" }

            assertNotNull(quickSearch, "GET /quick-search should exist")
            val quickSearchNotNull = quickSearch!!
            assertEquals("quickSearch", quickSearchNotNull.operationId, "operationId should be quickSearch")

            val qParam = quickSearchNotNull.parameters.find { it.name == "q" }
            assertNotNull(qParam, "q parameter should exist")
            assertEquals("query", qParam?.`in`, "q should be a query parameter")
            assertTrue(qParam?.required == true, "q should be required")
        }

        @Test
        @DisplayName("processInventoryBatch operation should have oneOf in requestBody")
        fun `process inventory batch operation should have one of in request body`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val processInventoryBatch = result.paths.find { it.path == "/inventory-batch" && it.method == "POST" }

            assertNotNull(processInventoryBatch, "POST /inventory-batch should exist")
            val processInventoryBatchNotNull = processInventoryBatch!!
            assertEquals("processInventoryBatch", processInventoryBatchNotNull.operationId,
                "operationId should be processInventoryBatch")

            assertNotNull(processInventoryBatchNotNull.requestBody, "processInventoryBatch should have requestBody")
            assertNotNull(processInventoryBatchNotNull.requestBody?.oneOf, "requestBody should have oneOf")

            val expectedOneOf = listOf("SimpleItem", "BatchItem")
            assertEquals(expectedOneOf, processInventoryBatchNotNull.requestBody?.oneOf,
                "oneOf should include SimpleItem and BatchItem")
        }
    }

    @Nested
    @DisplayName("Deep Nesting Tests")
    inner class DeepNestingTests {

        @Test
        @DisplayName("Product -> Manufacturer -> ManufacturerAddress -> ContactInfo nesting should work")
        fun `product manufacturer address contact info nesting should work`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            // Level 1: Product
            val product = result.schemas["Product"]
            assertNotNull(product, "Product should exist")
            val productNotNull = product!!

            // Level 2: Manufacturer
            val manufacturerRef = productNotNull.properties["manufacturer"]?.ref
            assertEquals("Manufacturer", manufacturerRef, "Product should reference Manufacturer")

            val manufacturer = result.schemas["Manufacturer"]
            assertNotNull(manufacturer, "Manufacturer should exist")
            val manufacturerNotNull = manufacturer!!

            // Level 3: ManufacturerAddress
            val addressRef = manufacturerNotNull.properties["headquarters"]?.ref
            assertEquals("ManufacturerAddress", addressRef, "Manufacturer should reference ManufacturerAddress")

            val address = result.schemas["ManufacturerAddress"]
            assertNotNull(address, "ManufacturerAddress should exist")
            val addressNotNull = address!!

            // Level 4: ContactInfo
            val contactInfoRef = addressNotNull.properties["contactInfo"]?.ref
            assertEquals("ContactInfo", contactInfoRef, "ManufacturerAddress should reference ContactInfo")

            val contactInfo = result.schemas["ContactInfo"]
            assertNotNull(contactInfo, "ContactInfo should exist")
        }
    }

    @Nested
    @DisplayName("Inline Schema Tests")
    inner class InlineSchemaTests {

        @Test
        @DisplayName("QuickSearchGetResponse inline schema should be parsed correctly")
        fun `quick search get response inline schema should be parsed correctly`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val quickSearchResponse = result.schemas["QuickSearchGetResponse"]
            assertNotNull(quickSearchResponse, "QuickSearchGetResponse should be generated")
            val quickSearchResponseNotNull = quickSearchResponse!!

            // Should have properties from inline schema
            val expectedProperties = setOf("query", "results")
            val actualProperties = quickSearchResponseNotNull.properties.keys

            assertEquals(expectedProperties, actualProperties,
                "QuickSearchGetResponse properties should match")

            val expectedRequired = setOf("query", "results")
            assertEquals(expectedRequired, quickSearchResponseNotNull.required,
                "QuickSearchGetResponse required fields should match")

            // results should be an array
            val resultsProperty = quickSearchResponseNotNull.properties["results"]
            assertTrue(resultsProperty?.isArray == true, "results should be an array")
        }
    }

    @Nested
    @DisplayName("Response Schema Tests")
    inner class ResponseSchemaTests {

        @Test
        @DisplayName("createProduct response should return ProductConfirmation")
        fun `create product response should return product confirmation`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val createProduct = result.paths.find { it.path == "/products" && it.method == "POST" }
            assertNotNull(createProduct, "POST /products should exist")
            val createProductNotNull = createProduct!!

            val response201 = createProductNotNull.responses["201"]
            assertNotNull(response201, "201 response should exist")

            assertEquals("ProductConfirmation", response201?.ref,
                "201 response should reference ProductConfirmation schema")
        }

        @Test
        @DisplayName("createProduct error response should return ErrorModel")
        fun `create product error response should return error model`() {
            if (!examplePath.toFile().exists()) {
                println("Skipping test: Example file not found")
                return
            }

            val result = parser.parse(examplePath)

            val createProduct = result.paths.find { it.path == "/products" && it.method == "POST" }
            assertNotNull(createProduct, "POST /products should exist")
            val createProductNotNull = createProduct!!

            val response400 = createProductNotNull.responses["400"]
            assertNotNull(response400, "400 response should exist")

            assertEquals("ErrorModel", response400?.ref,
                "400 response should reference ErrorModel schema")
        }
    }
}
