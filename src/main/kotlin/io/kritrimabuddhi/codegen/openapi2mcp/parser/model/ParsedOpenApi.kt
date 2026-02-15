package io.kritrimabuddhi.codegen.openapi2mcp.parser.model

/**
 * Internal representation of a parsed OpenAPI specification.
 *
 * @property openapiVersion The OpenAPI version (3.0 or 3.1)
 * @property info API information (title, version, description)
 * @property schemas Map of schema name to SchemaModel
 * @property paths List of PathModel representing API endpoints
 */
data class ParsedOpenApi(
  val openapiVersion: String,
  val info: ApiInfo,
  val schemas: Map<String, SchemaModel>,
  val paths: List<PathModel>
)

/**
 * API metadata information.
 *
 * @property title The API title
 * @property version The API version
 * @property description Optional description
 */
data class ApiInfo(
  val title: String,
  val version: String,
  val description: String?
)

/**
 * Internal representation of a schema definition.
 *
 * @property name The schema name
 * @property description Optional description
 * @property type The type (object, string, number, etc.)
 * @property properties Map of property name to PropertyInfo
 * @property required Set of required property names
 * @property enum Optional enum values
 * @property format Optional format (e.g., int32, date-time)
 */
data class SchemaModel(
  val name: String,
  val description: String?,
  val type: String?,
  val properties: Map<String, PropertyInfo>,
  val required: Set<String>,
  val enum: List<String>?,
  val format: String?,
  val ref: String?
)

/**
 * Information about a schema property.
 *
 * @property type The property type
 * @property format Optional format
 * @property description Optional description
 * @property isNullable Whether the property is nullable (not in required)
 * @property ref Reference to another schema ($ref)
 * @property isArray Whether this is an array type
 * @property arrayItemRef Reference type for array items
 */
data class PropertyInfo(
  val type: String?,
  val format: String?,
  val description: String?,
  val isNullable: Boolean,
  val ref: String?,
  val isArray: Boolean,
  val arrayItemRef: String?,
  val arrayItemType: String? = null,
  val arrayItemFormat: String? = null,
  val enum: List<String>?,
  val defaultValue: String?
)

/**
 * Internal representation of a path and its operations.
 *
 * @property path The path template (e.g., /users/{id})
 * @property method HTTP method (GET, POST, PUT, DELETE, PATCH)
 * @property operationId Unique operation identifier
 * @property summary Optional summary
 * @property description Optional description
 * @property parameters List of path, query, and header parameters
 * @property requestBody Request body information
 * @property responses Map of status codes to response information
 * @property tags List of tags
 */
data class PathModel(
  val path: String,
  val method: String,
  val operationId: String?,
  val summary: String?,
  val description: String?,
  val parameters: List<ParameterInfo>,
  val requestBody: RequestBodyInfo?,
  val responses: Map<String, ResponseInfo>,
  val tags: List<String>
)

/**
 * Information about an operation parameter.
 *
 * @property name Parameter name
 * @property `in` Parameter location (path, query, header, cookie)
 * @property description Optional description
 * @property required Whether the parameter is required
 * @property type Parameter type
 * @property format Optional format
 * @property ref Reference to a schema
 * @property isArray Whether this is an array type
 */
data class ParameterInfo(
  val name: String,
  val `in`: String,
  val description: String?,
  val required: Boolean,
  val type: String?,
  val format: String?,
  val ref: String?,
  val isArray: Boolean,
  val arrayItemRef: String?,
  val arrayItemType: String? = null,
  val arrayItemFormat: String? = null,
  val defaultValue: String?
)

/**
 * Information about a request body.
 *
 * @property description Optional description
 * @property required Whether the request body is required
 * @property contentType Content type (e.g., application/json)
 * @property ref Reference to the schema
 * @property isArray Whether the body is an array
 */
data class RequestBodyInfo(
  val description: String?,
  val required: Boolean,
  val contentType: String,
  val type: String? = null,
  val format: String? = null,
  val ref: String?,
  val isArray: Boolean,
  val arrayItemType: String? = null,
  val arrayItemFormat: String? = null
)

/**
 * Information about a response.
 *
 * @property statusCode HTTP status code
 * @property description Description of the response
 * @property ref Reference to the schema
 * @property isArray Whether the response is an array
 * @property isNoContent Whether this is a 204 No Content response
 */
data class ResponseInfo(
  val statusCode: String,
  val description: String,
  val type: String? = null,
  val format: String? = null,
  val ref: String?,
  val isArray: Boolean,
  val arrayItemType: String? = null,
  val arrayItemFormat: String? = null,
  val isNoContent: Boolean
)