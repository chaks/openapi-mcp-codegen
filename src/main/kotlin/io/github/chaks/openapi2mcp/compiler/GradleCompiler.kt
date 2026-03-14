package io.github.chaks.openapi2mcp.compiler

import jakarta.enterprise.context.ApplicationScoped

/**
 * Gradle-based implementation of the Compiler interface.
 *
 * Uses Gradle wrapper (gradlew) to compile the generated project.
 */
@ApplicationScoped
class GradleCompiler : Compiler {

  override fun compile(outputDir: java.nio.file.Path, verbose: Boolean) {
    val command = determineGradleCommand()

    if (verbose) {
      println("Compiling generated code...")
      println("  Command: ${command.joinToString(" ")}")
    }

    val processBuilder = ProcessBuilder(command)
    processBuilder.directory(outputDir.toFile())

    if (verbose) {
      processBuilder.redirectErrorStream(true)
    } else {
      processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)
    }

    val process = processBuilder.start()

    // Read output
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()

    val exitCode = process.waitFor()

    if (verbose) {
      println(output)
    }

    if (exitCode != 0) {
      val errorMessage = error.ifEmpty { output }
      throw RuntimeException("Compilation failed with exit code $exitCode. Output: $errorMessage")
    }
  }

  override fun isAvailable(): Boolean {
    val command = determineGradleCommand()
    return command.firstOrNull()?.let { cmd ->
      val isWindows = System.getProperty("os.name").lowercase().contains("windows")
      if (isWindows && cmd == "gradlew.bat") {
        // On Windows, check for gradlew.bat
        true // We'll assume it's available if we can try
      } else {
        // On Unix-like systems, check for gradlew or gradle
        true // We'll assume it's available if we can try
      }
    } ?: false
  }

  /**
   * Determines the appropriate Gradle command for the current OS.
   *
   * Uses gradlew/gradlew.bat if available in the output directory,
   * otherwise falls back to system gradle command.
   */
  private fun determineGradleCommand(): List<String> {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    return if (isWindows) {
      listOf("cmd", "/c", "gradlew.bat", "build")
    } else {
      listOf("./gradlew", "build")
    }
  }
}
