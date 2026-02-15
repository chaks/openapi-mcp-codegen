# Type Mapping

Reference for how OpenAPI types are mapped to Kotlin types in generated code.

## Primitive Types

| OpenAPI Type | Format | Kotlin Type | Notes |
|--------------|--------|-------------|-------|
| `string` | - | `String` | Default string type |
| `string` | `date-time` | `String` | ISO-8601 date-time strings |
| `string` | `date` | `String` | ISO-8601 date strings |
| `string` | `time` | `String` | ISO-8601 time strings |
| `string` | `email` | `String` | Email address strings |
| `string` | `uuid` | `String` | UUID strings |
| `string` | `uri` | `String` | URI strings |
| `string` | `url` | `String` | URL strings |
| `string` | `byte` | `String` | Base64 encoded byte strings |
| `string` | `binary` | `String` | Binary data strings |
| `integer` | - | `Int` | Default integer (int32) |
| `integer` | `int32` | `Int` | 32-bit signed integer |
| `integer` | `int64` | `Long` | 64-bit signed integer |
| `number` | - | `Double` | Default number (double) |
| `number` | `float` | `Float` | 32-bit floating point |
| `number` | `double` | `Double` | 64-bit floating point |
| `boolean` | - | `Boolean` | Boolean value |

## Complex Types

| OpenAPI Type | Kotlin Type | Notes |
|--------------|-------------|-------|
| `array` | `List<T>` | Generic list of type T |
| `object` | Data class | Generated data class from schema |
| `object` (additionalProperties) | `Map<String, T>` | String-keyed map |
| `enum` | String enum | Enum values as string constants |

## Nullability

### Required Fields

Fields marked as `required` in the OpenAPI schema are **non-nullable**:

```kotlin
data class User(
    val id: Long,        // Required - non-nullable
    val name: String     // Required - non-nullable
)
```

### Optional Fields

Fields NOT in the `required` array are **nullable**:

```kotlin
data class User(
    val id: Long?,        // Optional - nullable
    val name: String?     // Optional - nullable
)
```

### Default Values

Fields with `default` values are non-nullable:

```yaml
User:
  type: object
  properties:
    status:
      type: string
      default: active
```

```kotlin
data class User(
    val status: String = "active"  // Non-nullable with default
)
```

## Examples

### Simple Object

**OpenAPI:**
```yaml
User:
  type: object
  required:
    - id
    - name
  properties:
    id:
      type: integer
      format: int64
    name:
      type: string
    email:
      type: string
      format: email
    active:
      type: boolean
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class User(
    @JsonProperty("id")
    @JsonbProperty("id")
    val id: Long,

    @JsonProperty("name")
    @JsonbProperty("name")
    val name: String,

    @JsonProperty("email")
    @JsonbProperty("email")
    val email: String? = null,

    @JsonProperty("active")
    @JsonbProperty("active")
    val active: Boolean? = null
)
```

### Nested Object

**OpenAPI:**
```yaml
Pet:
  type: object
  properties:
    id:
      type: integer
    name:
      type: string
    category:
      $ref: '#/components/schemas/Category'

Category:
  type: object
  properties:
    id:
      type: integer
    name:
      type: string
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class Pet(
    @JsonProperty("id")
    @JsonbProperty("id")
    val id: Int? = null,

    @JsonProperty("name")
    @JsonbProperty("name")
    val name: String? = null,

    @JsonProperty("category")
    @JsonbProperty("category")
    val category: Category? = null
)

@kotlinx.serialization.Serializable
data class Category(
    @JsonProperty("id")
    @JsonbProperty("id")
    val id: Int? = null,

    @JsonProperty("name")
    @JsonbProperty("name")
    val name: String? = null
)
```

### Array of Objects

**OpenAPI:**
```yaml
PetList:
  type: object
  properties:
    pets:
      type: array
      items:
        $ref: '#/components/schemas/Pet'
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class PetList(
    @JsonProperty("pets")
    @JsonbProperty("pets")
    val pets: List<Pet>? = null
)
```

### Enum

**OpenAPI:**
```yaml
Status:
  type: string
  enum:
    - available
    - pending
    - sold
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class Status(
    @JsonProperty("status")
    @JsonbProperty("status")
    val status: String? = null
    // Enum values are documented in KDoc
)
```

### Map (Additional Properties)

**OpenAPI:**
```yaml
Metadata:
  type: object
  additionalProperties:
    type: string
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class Metadata(
    @JsonProperty("additionalProperties")
    @JsonbProperty("additionalProperties")
    val additionalProperties: Map<String, String>? = null
)
```

### AllOf Composition

**OpenAPI:**
```yaml
ExtendedPet:
  allOf:
    - $ref: '#/components/schemas/Pet'
    - type: object
      properties:
        vaccinated:
          type: boolean
```

**Generated Kotlin:**
```kotlin
@kotlinx.serialization.Serializable
data class ExtendedPet(
    @JsonProperty("id")
    @JsonbProperty("id")
    val id: Long? = null,

    @JsonProperty("name")
    @JsonbProperty("name")
    val name: String? = null,

    // ... other Pet fields ...

    @JsonProperty("vaccinated")
    @JsonbProperty("vaccinated")
    val vaccinated: Boolean? = null
)
```

## Format Handling

### Date and Time

All date-time formats are handled as strings. The generator does not convert to `java.time` types to maintain simplicity and flexibility.

```yaml
Event:
  type: object
  properties:
    createdAt:
      type: string
      format: date-time
    eventDate:
      type: string
      format: date
```

```kotlin
data class Event(
    val createdAt: String? = null,  // ISO-8601 date-time string
    val eventDate: String? = null   // ISO-8601 date string
)
```

## Annotations

All generated data classes include comprehensive annotations for serialization:

- **Jackson**: `@JsonProperty`, `@JsonInclude`, `@JsonPropertyOrder`
- **JSON-B**: `@JsonbProperty`
- **Kotlin Serialization**: `@kotlinx.serialization.Serializable`

This ensures compatibility with multiple serialization frameworks.

## Related Documentation

- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [OpenAPI Support](OpenAPI-Support) - Supported OpenAPI features
- [Examples](Examples) - Real-world usage examples
