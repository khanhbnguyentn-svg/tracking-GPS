package com.internal.tracker

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigTest {
    private fun repositoryRoot(): File {
        val start = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${start.absolutePath}")
    }

    private fun projectFile(relativePath: String): String =
        File(repositoryRoot(), relativePath).readText()

    @Test
    fun `package name stays stable`() {
        assertEquals("com.internal.tracker", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `project pins Android SET 3 release identity and encrypted storage`() {
        val appBuild = projectFile("app/build.gradle.kts")
        val catalog = projectFile("gradle/libs.versions.toml")
        val manifest = projectFile("app/src/main/AndroidManifest.xml")

        assertTrue(appBuild.contains("minSdk = 26"))
        assertTrue(appBuild.contains("targetSdk = 36"))
        assertTrue(appBuild.contains("versionCode = 7"))
        assertTrue(appBuild.contains("versionName = \"3.0.0\""))
        assertTrue(catalog.contains("room = \"2.8.4\""))
        assertTrue(catalog.contains("sqlcipher = \"4.17.0\""))
        assertTrue(catalog.contains("sqlite = \"2.7.0\""))
        assertTrue(appBuild.contains("implementation(libs.sqlcipher.android)"))
        assertTrue(appBuild.contains("implementation(libs.androidx.sqlite)"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }

    @Test
    fun `android SET branch excludes legacy subsystems and declarations`() {
        val root = repositoryRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        listOf("server", "gps-receiver", "config").forEach { path ->
            val containsFiles = File(root, path).walkTopDown().any(File::isFile)
            assertFalse("Legacy path must not contain files: $path", containsFiles)
        }
        listOf(
            ".schedule.ScheduleReceiver",
            ".tracking.TrackingService",
            ".tracking.VehicleActivityReceiver",
            "androidx.core.content.FileProvider",
        ).forEach { declaration ->
            assertFalse(
                "Legacy manifest declaration must be absent: $declaration",
                manifest.contains(declaration),
            )
        }
    }
}
