# OpenAPI Support

Complete reference for OpenAPI 3.0/3.1 features supported by the code generator.

## Supported OpenAPI Versions

- **OpenAPI 3.0.x**
- **OpenAPI 3.1.x**

## Supported Features

### Schema Definitions (`components/schemas`)

Full support for schema definitions including:

- Primitive types (string, integer, number, boolean)
- Complex types (array, object)
- Nested schemas via `$ref`
- Required/optional fields
- Default values
- Enum values
- Array types with `items`
- Object properties with types

### Path Definitions

Complete path definition support:

- All HTTP methods: GET, POST, PUT, DELETE, PATCH
- Path parameters (e.g., `/users/{id}`)
- Operation IDs
- Descriptions and summaries
- Tags for grouping
- Deprecated flag

### Parameters

Support for all parameter locations:

| Parameter Location | Support | Notes |
|-------------------|---------|-------|
| `path` | ✅ Full | Mapped to `@PathParam` |
| `query` | ✅ Full | Mapped to `@QueryParam` |
| `header` | ✅ Full | Mapped to `@HeaderParam` |
| `cookie` | ✅ Full | Mapped to `@CookieParam` |

### Request Bodies

Full request body support:

- Content types (`application/json`, `application/xml`, etc.)
- Schema references (`$ref`)
- Inline schema definitions
- Required/optional bodies
- Multipart form data (limited)

### Responses

Response support includes:

- Status codes (200, 201, 400, 404, 500, etc.)
- Default responses
- Response schemas via `$ref`
- Multiple content types
- Array responses

### Composition Patterns

Limited support for schema composition:

| Pattern | Support | Notes |
|---------|---------|-------|
| `allOf` | ⚠️ Partial | Fields are merged, no type hierarchy |
| `anyOf` | ❌ Not supported | Use alternative schemas |
| `oneOf` | ❌ Not supported | Use alternative schemas |

### Extensions

OpenAPI extensions (`x-*`) are **ignored** during code generation.

## Limitations and Notes

### Not Supported

The following OpenAPI features are NOT supported:

- **Discriminator**: Polymorphic type handling
- **Examples**: Example values are ignored
- **External Docs**: External documentation references
- **Callbacks**: Callback definitions
- **Links**: Link objects
- **Servers**: Server configurations
- **Security Schemes**: Security definitions (handled manually in Quarkus)
- **Components除了 Schemas**: Only schemas are processed

### Partial Support

- **`allOf`**: Fields are merged but no type hierarchy is created
- **`additionalProperties`**: Mapped to `Map<String, T>` for simple types
- **Pattern**: Regex patterns are documented but not enforced
- **Min/Max/MinLength/MaxLength**: Documented but not validated

### Type Mapping Limitations

- All date-time formats are strings (no `java.time` types)
- Binary data is handled as strings
- No support for custom format types
- Enum values are not validated at runtime

## Example OpenAPI Specification

Here's a comprehensive example demonstrating supported features:

```yaml
openapi: 3.0.3
info:
  title: Petstore API
  version: 1.0.0
  description: A sample Pet Store Server based on the OpenAPI 3.0 specification.

servers:
  - url: https://petstore.swagger.io/v2

paths:
  /pet:
    post:
      tags:
        - pet
      summary: Add a new pet to the store
      description: Add a new pet to the store
      operationId: addPet
      requestBody:
        description: Pet object that needs to be added to the store
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
        required: true
      responses:
        '200':
          description: Successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
        '405':
          description: Invalid input

  /pet/{petId}:
    get:
      tags:
        - pet
      summary: Find pet by ID
      description: Returns a single pet
      operationId: getPetById
      parameters:
        - name: petId
          in: path
          description: ID of pet to return
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
        '400':
          description: Invalid ID supplied
        '404':
          description: Pet not found

  /pet/findByStatus:
    get:
      tags:
        - pet
      summary: Finds Pets by status
      description: Multiple status values can be provided with comma separated strings
      operationId: findPetsByStatus
      parameters:
        - name: status
          in: query
          description: Status values that need to be considered for filter
          required: true
          explode: true
          schema:
            type: array
            items:
              type: string
              enum:
                - available
                - pending
                - sold
            default: available
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Pet'
        '400':
          description: Invalid status value

components:
  schemas:
    Category:
      type: object
      properties:
        id:
          type: integer
          format: int64
          example: 1
        name:
          type: string
          example: Dogs

    Tag:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string

    Pet:
      type: object
      required:
        - name
        - photoUrls
      properties:
        id:
          type: integer
          format: int64
          example: 10
        category:
          $ref: '#/components/schemas/Category'
        name:
          type: string
          example: doggie
        photoUrls:
          type: array
          items:
            type: string
        tags:
          type: array
          items:
            $ref: '#/components/schemas/Tag'
        status:
          type: string
          description: pet status in the store
          enum:
            - available
            - pending
            - sold
```

## Best Practices

### For Best Results

1. **Use explicit types**: Avoid `type: object` without `additionalProperties`
2. **Document required fields**: Always mark required fields explicitly
3. **Use operation IDs**: Provide meaningful operation IDs for better generated method names
4. **Avoid complex composition**: Prefer explicit schemas over `allOf`/`anyOf`
5. **Validate first**: Use an OpenAPI validator before running the generator

### Naming Conventions

- Use PascalCase for schema names: `User`, `PetOrder`
- Use camelCase for property names: `firstName`, `lastName`
- Use kebab-case or camelCase for path parameters: `userId` or `user-id`
- Provide meaningful operation IDs: `getUserById`, `createPet`

## Related Documentation

- [Type Mapping](Type-Mapping) - OpenAPI to Kotlin type conversion
- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [Examples](Examples) - Real-world usage examples