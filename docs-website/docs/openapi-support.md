---
sidebar_position: 5
---

# OpenAPI Specification Support

## Supported Features

The generator supports the following OpenAPI 3.0/3.1 features:

| Feature | Support |
|---------|---------|
| Schema definitions (`components/schemas`) | <span class="check-mark">✓</span> |
| Path definitions with HTTP methods | <span class="check-mark">✓</span> |
| GET, POST, PUT, DELETE, PATCH | <span class="check-mark">✓</span> |
| Parameters (path, query, header, cookie) | <span class="check-mark">✓</span> |
| Request bodies | <span class="check-mark">✓</span> |
| Responses with schema references | <span class="check-mark">✓</span> |
| Arrays and nested objects | <span class="check-mark">✓</span> |
| Enum values | <span class="check-mark">✓</span> |
| Required/optional fields | <span class="check-mark">✓</span> |

## Type Mapping

OpenAPI types are mapped to Kotlin types as follows:

| OpenAPI Type | Format    | Kotlin Type |
|--------------|-----------|-------------|
| `string`     | -         | `String`    |
| `string`     | `date-time` | `String`  |
| `string`     | `uuid`    | `String`    |
| `integer`    | `int32`   | `Int`       |
| `integer`    | `int64`   | `Long`      |
| `number`     | `float`   | `Float`     |
| `number`     | `double`  | `Double`    |
| `boolean`    | -         | `Boolean`   |
| `array`      | -         | `List<T>`   |
| `object`     | -         | Data class  |

## Example OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: Petstore API
  version: 1.0.0
paths:
  /pet/findByStatus:
    get:
      summary: Finds Pets by status
      parameters:
        - name: status
          in: query
          schema:
            type: array
            items:
              type: string
      responses:
        '200':
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Pet'
components:
  schemas:
    Pet:
      type: object
      required:
        - name
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        status:
          type: string
          enum: [available, pending, sold]
```

## Limitations

- Not all OpenAPI features are currently supported
- Advanced authentication schemes require manual implementation
- Complex `oneOf`, `anyOf`, `allOf` schemas have limited support

For unsupported features, you may need to manually adjust the generated code.