package io.kritrimabuddhi.codegen.openapi2mcp.generator

import io.kritrimabuddhi.codegen.openapi2mcp.cli.CliOptions
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Generates Gradle build configuration files for the generated project.
 *
 * Creates build.gradle.kts and settings.gradle.kts files with proper
 * Quarkus and Kotlin dependencies for compiling the generated code.
 */
@ApplicationScoped
class BuildConfigGenerator {

  /**
   * Generate all Gradle configuration files.
   *
   * @param options CLI options containing output directory and package info
   */
  fun generate(options: CliOptions) {
    generateBuildGradle(options)
    generateSettingsGradle(options)
    generateGradleWrapper(options)
  }

  private fun generateBuildGradle(options: CliOptions) {
    val buildGradleContent = """
plugins {
    kotlin("jvm") version "2.2.21"
    id("io.quarkus") version "3.31.3"
    id("org.kordamp.gradle.jandex") version "0.13.2"
}

group = "${options.rootPackage}"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus:quarkus-bom:3.31.3"))
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-langchain4j-bom:3.31.3"))
    implementation(enforcedPlatform("io.quarkiverse.mcp:quarkus-mcp-server-bom:1.8.0"))

    // Quarkus REST dependencies
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")

    // Quarkus LangChain4j
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-core")

    // Quarkus MCP Server
    implementation("io.quarkiverse.mcp:quarkus-mcp-server-http")

    // Jackson Kotlin module
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    // Exclude tool package from compilation
    exclude("**/tool/**")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// Configure the main JAR task to exclude tool package
tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output) {
        exclude("**/tool/**")
    }
}

// Configure Quarkus build to exclude tool package
tasks.named("quarkusBuild") {
    doFirst {
        // Tool package is excluded from JAR
    }
}

// Task for building domain+client only JAR (alias for standard jar task)
tasks.register<Jar>("domainClientJar") {
    dependsOn("jar")
    archiveClassifier.set("domain-client")
    // This is now an alias as jar already excludes tool package
    enabled = false
}
    """.trimIndent()

    val buildGradlePath = options.output.resolve("build.gradle.kts")
    buildGradlePath.writeText(buildGradleContent)
  }

  private fun generateSettingsGradle(options: CliOptions) {
    val settingsGradleContent = """
rootProject.name = "${options.projectName}"
    """.trimIndent()

    val settingsGradlePath = options.output.resolve("settings.gradle.kts")
    settingsGradlePath.writeText(settingsGradleContent)
  }

  private fun generateGradleWrapper(options: CliOptions) {
    val outputDir = options.output

    // Create gradle wrapper directory
    val wrapperDir = outputDir.resolve("gradle/wrapper")
    Files.createDirectories(wrapperDir)

    // Copy gradle-wrapper.jar from current project
    val currentProjectDir = Path.of(System.getProperty("user.dir"))
    val sourceWrapperJar = currentProjectDir.resolve("gradle/wrapper/gradle-wrapper.jar")

    if (Files.exists(sourceWrapperJar)) {
      val targetWrapperJar = wrapperDir.resolve("gradle-wrapper.jar")
      Files.copy(sourceWrapperJar, targetWrapperJar)
    }

    // Generate gradle-wrapper.properties
    val wrapperProperties = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
    """.trimIndent()

    val wrapperPropertiesPath = wrapperDir.resolve("gradle-wrapper.properties")
    wrapperPropertiesPath.writeText(wrapperProperties)

    // Copy gradlew script if it exists in the current project
    val sourceGradlew = currentProjectDir.resolve("gradlew")
    if (Files.exists(sourceGradlew)) {
      val targetGradlew = outputDir.resolve("gradlew")
      Files.copy(sourceGradlew, targetGradlew)
      // Make gradlew executable
      targetGradlew.toFile().setExecutable(true)
    }

    // Copy gradlew.bat if it exists in the current project (for Windows)
    val sourceGradlewBat = currentProjectDir.resolve("gradlew.bat")
    if (Files.exists(sourceGradlewBat)) {
      val targetGradlewBat = outputDir.resolve("gradlew.bat")
      Files.copy(sourceGradlewBat, targetGradlewBat)
    }
  }
}
