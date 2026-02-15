# Examples

Real-world examples demonstrating various use cases for OpenAPI MCP Codegen.

## Table of Contents

1. [Basic SDK Generation](#basic-sdk-generation)
2. [Petstore API](#petstore-api)
3. [Custom API Specification](#custom-api-specification)
4. [Using Generated REST Client](#using-generated-rest-client)
5. [Using MCP Tools with LangChain4j](#using-mcp-tools-with-langchain4j)
6. [Building Selective JAR](#building-selective-jar)

---

## Basic SDK Generation

### Step 1: Prepare Your OpenAPI Spec

```yaml
# my-api.yaml
openapi: 3.0.3
info:
  title: My API
  version: 1.0.0
paths:
  /users:
    get:
      summary: List all users
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'
components:
  schemas:
    User:
      type: object
      required:
        - id
        - name
      properties:
        id:
          type: integer
        name:
          type: string
        email:
          type: string
```

### Step 2: Generate Code

```bash
./gradlew run --args="-i my-api.yaml -o ./generated -r com.mycompany.api"
```

### Step 3: Build Generated Code

```bash
cd generated
./gradlew build
```

---

## Petstore API

The repository includes a sample Petstore API specification for testing.

### Generate Petstore SDK

```bash
./gradlew run --args="-i examples/petstore.yaml -o ./generated-petstore -r io.swagger.petstore"
```

### Generated Structure

```
generated-petstore/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/io/swagger/petstore/
│   ├── domain/
│   │   ├── Pet.kt
│   │   ├── Category.kt
│   │   ├── Order.kt
│   │   ├── User.kt
│   │   └── Tag.kt
│   ├── client/
│   │   └── PetstoreClient.kt
│   └── tool/
│       └── PetstoreTools.kt
└── src/main/resources/
    └── application.properties
```

### Build Petstore SDK

```bash
cd generated-petstore
./gradlew build
```

---

## Custom API Specification

This example shows a more complex API with multiple endpoints and nested types.

### OpenAPI Specification

```yaml
# blog-api.yaml
openapi: 3.0.3
info:
  title: Blog API
  version: 1.0.0
  description: A simple blog API
paths:
  /posts:
    get:
      summary: List all posts
      tags:
        - posts
      parameters:
        - name: limit
          in: query
          schema:
            type: integer
            default: 10
        - name: offset
          in: query
          schema:
            type: integer
            default: 0
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Post'
    post:
      summary: Create a new post
      tags:
        - posts
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PostCreate'
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Post'

  /posts/{postId}:
    get:
      summary: Get a post by ID
      tags:
        - posts
      parameters:
        - name: postId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Post'
    put:
      summary: Update a post
      tags:
        - posts
      parameters:
        - name: postId
          in: path
          required: true
          schema:
            type: integer
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PostUpdate'
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Post'
    delete:
      summary: Delete a post
      tags:
        - posts
      parameters:
        - name: postId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '204':
          description: No Content

components:
  schemas:
    Author:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        email:
          type: string

    Post:
      type: object
      required:
        - id
        - title
        - content
        - author
      properties:
        id:
          type: integer
        title:
          type: string
        content:
          type: string
        author:
          $ref: '#/components/schemas/Author'
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    PostCreate:
      type: object
      required:
        - title
        - content
        - authorId
      properties:
        title:
          type: string
        content:
          type: string
        authorId:
          type: integer

    PostUpdate:
      type: object
      properties:
        title:
          type: string
        content:
          type: string
```

### Generate Blog API SDK

```bash
./gradlew run --args="
  -i blog-api.yaml
  -o ./blog-sdk
  -r com.example.blog
  -v
"
```

---

## Using Generated REST Client

### Configuration

Update `application.properties` with the base URL:

```properties
# src/main/resources/application.properties
quarkus.application.name=Blog SDK
quarkus.http.port=8080

# Rest client configuration
blog-api/mp-rest/url=https://api.example.com/v1
blog-api/mp-rest/scope=javax.enterprise.context.ApplicationScoped
```

### Usage in Quarkus Application

```kotlin
package com.example.app

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RestClient
import com.example.blog.client.BlogClient
import com.example.blog.domain.Post

@Path("/blog")
class BlogResource {

    @RestClient
    lateinit var blogClient: BlogClient

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getPosts(): List<Post> {
        return blogClient.getPosts(limit = 10, offset = 0)
    }

    @GET
    @Path("/{postId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPost(postId: Long): Post {
        return blogClient.getPostById(postId)
    }
}
```

---

## Using MCP Tools with LangChain4j

### Injecting Tools

```kotlin
package com.example.app

import dev.langchain4j.agent.tool.Tool
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class BlogAssistant {

    @RestClient
    lateinit var blogClient: BlogClient

    @Tool("Get all blog posts")
    fun getAllPosts(): List<Post> {
        return blogClient.getPosts()
    }

    @Tool("Get a blog post by ID")
    fun getPostById(postId: Long): Post {
        return blogClient.getPostById(postId)
    }

    @Tool("Create a new blog post")
    fun createPost(title: String, content: String, authorId: Long): Post {
        val postCreate = PostCreate(
            title = title,
            content = content,
            authorId = authorId
        )
        return blogClient.createPost(postCreate)
    }
}
```

### Using with AI Services

```kotlin
package com.example.app

import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

interface Assistant {
    @SystemMessage("You are a helpful blog assistant.")
    fun chat(@UserMessage userMessage: String): String
}

@ApplicationScoped
class BlogService {

    @Inject
    lateinit var blogAssistant: BlogAssistant

    @Inject
    lateinit var chatModel: ChatLanguageModel

    fun createAssistant(): Assistant {
        return AiServices.builder(Assistant::class.java)
            .chatLanguageModel(chatModel)
            .tools(blogAssistant)
            .build()
    }

    fun interactWithUser(userMessage: String): String {
        val assistant = createAssistant()
        return assistant.chat(userMessage)
    }
}
```

---

## Building Selective JAR

### Build Domain + Client Only

If you only need the data models and REST client (without MCP tools):

```bash
cd generated
./gradlew domainClientJar
```

This creates a JAR file at:
```
build/libs/generated-sdk-domain-client.jar
```

### Use Case

Useful when:
- You want a lightweight SDK without AI integration
- The tool layer is used in a separate module
- You're building a library that doesn't include AI features

---

## Advanced: Verbose Debugging

When encountering issues with your OpenAPI specification, use verbose mode:

```bash
./gradlew run --args="
  -i my-api.yaml
  -o ./generated
  -r com.mycompany.api
  -v
"
```

Verbose output shows:
- Parsing progress
- Each schema being processed
- Type mapping information
- File generation status
- Any warnings or errors

---

## Integration Examples

### With Spring Boot (Alternative to Quarkus)

The generated domain classes can be used with Spring Boot:

```kotlin
@Service
class PostService(
    @RestClient
    private val blogClient: BlogClient
) {
    fun getPosts(limit: Int = 10): List<Post> {
        return blogClient.getPosts(limit = limit, offset = 0)
    }
}
```

### With Micronaut

```kotlin
@Controller("/api/posts")
class PostController(
    @Client("/v1")
    private val blogClient: BlogClient
) {
    @Get
    fun listPosts(): List<Post> {
        return blogClient.getPosts()
    }
}
```

---

## Related Documentation

- [Getting Started](Getting-Started) - Installation and first steps
- [Generated Code Structure](Generated-Code-Structure) - What gets generated
- [CLI Reference](CLI-Reference) - Complete command-line options
