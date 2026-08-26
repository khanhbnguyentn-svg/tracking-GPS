package com.internal.tracker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class PeriodicBranchScopeTest {
    private fun repositoryRoot(): File {
        val start = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${start.absolutePath}")
    }

    @Test
    fun `periodic branch excludes unrelated subsystems`() {
        val root = repositoryRoot()
        listOf("server", "gps-receiver", "config").forEach { path ->
            val containsFiles = File(root, path).walkTopDown().any(File::isFile)
            assertFalse("Unrelated path must not contain files: $path", containsFiles)
        }
        listOf(
            "SYSTEM_REQUIREMENT_SPECIFICATION.md",
            "ANDROID_TECHNICAL_BUILD_SPEC",
        ).forEach { path ->
            assertFalse("SET 3.0 specification must be absent: $path", File(root, path).isFile)
        }
    }
}
