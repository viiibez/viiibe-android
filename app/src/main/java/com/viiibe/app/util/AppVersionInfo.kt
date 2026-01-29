package com.viiibe.app.util

import com.viiibe.app.BuildConfig

/**
 * Singleton that provides version information and utilities for the app.
 *
 * Exposes:
 * - VERSION_NAME: Semantic version string (e.g., "1.0.0")
 * - VERSION_CODE: Integer build number (e.g., 100)
 * - BUILD_TIME: ISO 8601 timestamp of when the build was created
 * - GIT_HASH: Short git commit hash (or "unknown" if not available)
 */
object AppVersionInfo {

    /** Semantic version string (e.g., "1.0.0") */
    val VERSION_NAME: String = BuildConfig.VERSION_NAME

    /** Integer version code (e.g., 100) */
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    /** ISO 8601 timestamp of the build (e.g., "2024-01-15T10:30:00Z") */
    val BUILD_TIME: String = BuildConfig.BUILD_TIME

    /** Short git commit hash (e.g., "abc1234") or "unknown" if not available */
    val GIT_HASH: String = BuildConfig.GIT_HASH

    /**
     * Returns a display-friendly version string.
     * Format: "v1.0.0 (build 100)"
     */
    fun getVersionDisplayString(): String {
        return "v$VERSION_NAME (build $VERSION_CODE)"
    }

    /**
     * Returns a detailed version string including git hash.
     * Format: "v1.0.0 (build 100) - abc1234"
     */
    fun getDetailedVersionString(): String {
        return "v$VERSION_NAME (build $VERSION_CODE) - $GIT_HASH"
    }

    /**
     * Compares this version against a required version.
     *
     * @param requiredVersionName The minimum required version (e.g., "1.0.0")
     * @return true if current version >= required version
     */
    fun isVersionAtLeast(requiredVersionName: String): Boolean {
        return compareVersions(VERSION_NAME, requiredVersionName) >= 0
    }

    /**
     * Compares this version code against a required version code.
     *
     * @param requiredVersionCode The minimum required version code
     * @return true if current version code >= required version code
     */
    fun isVersionCodeAtLeast(requiredVersionCode: Int): Boolean {
        return VERSION_CODE >= requiredVersionCode
    }

    /**
     * Compares two semantic version strings.
     *
     * Handles:
     * - Standard semver: "1.2.3" vs "1.2.0"
     * - Different lengths: "1.0" vs "1.0.0" (missing parts treated as 0)
     * - Pre-release: "1.0.0-beta" < "1.0.0" (pre-release is always lower)
     * - Leading zeros: "01.02.03" parsed as "1.2.3"
     *
     * @param version1 First version string (e.g., "1.2.3")
     * @param version2 Second version string (e.g., "1.2.0")
     * @return negative if version1 < version2, 0 if equal, positive if version1 > version2
     */
    fun compareVersions(version1: String, version2: String): Int {
        // Handle empty strings
        if (version1.isBlank() && version2.isBlank()) return 0
        if (version1.isBlank()) return -1
        if (version2.isBlank()) return 1

        // Strip pre-release suffix for comparison, but track if present
        val (clean1, prerelease1) = splitPrerelease(version1)
        val (clean2, prerelease2) = splitPrerelease(version2)

        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val v1 = parts1.getOrElse(i) { 0 }
            val v2 = parts2.getOrElse(i) { 0 }

            if (v1 != v2) {
                return v1 - v2
            }
        }

        // Versions are equal numerically - check pre-release
        // A pre-release version has lower precedence than a normal version
        // e.g., "1.0.0-alpha" < "1.0.0"
        return when {
            prerelease1 != null && prerelease2 == null -> -1
            prerelease1 == null && prerelease2 != null -> 1
            prerelease1 != null && prerelease2 != null -> prerelease1.compareTo(prerelease2)
            else -> 0
        }
    }

    /**
     * Splits a version string into its numeric part and optional pre-release suffix.
     * e.g., "1.0.0-beta.1" -> Pair("1.0.0", "beta.1")
     *       "1.0.0" -> Pair("1.0.0", null)
     */
    private fun splitPrerelease(version: String): Pair<String, String?> {
        val hyphenIndex = version.indexOf('-')
        return if (hyphenIndex >= 0) {
            Pair(version.substring(0, hyphenIndex), version.substring(hyphenIndex + 1))
        } else {
            Pair(version, null)
        }
    }

    /**
     * Parses a semantic version string into its components.
     *
     * Handles:
     * - Standard: "1.2.3" -> Triple(1, 2, 3)
     * - Two-part: "1.2" -> Triple(1, 2, 0)
     * - Pre-release: "1.2.3-beta" -> Triple(1, 2, 3) (suffix stripped)
     * - Leading zeros: "01.02.03" -> Triple(1, 2, 3)
     *
     * Returns null for:
     * - Empty/blank strings
     * - Non-numeric parts (e.g., "abc")
     * - Less than 2 parts (e.g., "1")
     * - More than 3 parts (e.g., "1.0.0.0") - not valid semver
     *
     * @param version Version string (e.g., "1.2.3")
     * @return Triple of (major, minor, patch) or null if parsing fails
     */
    fun parseVersion(version: String): Triple<Int, Int, Int>? {
        if (version.isBlank()) return null

        // Strip pre-release suffix (e.g., "-beta", "-alpha.1")
        val cleanVersion = splitPrerelease(version).first

        val parts = cleanVersion.split(".")

        // Require 2 or 3 parts (reject "1" and "1.0.0.0")
        if (parts.size < 2 || parts.size > 3) return null

        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = if (parts.size >= 3) parts[2].toIntOrNull() ?: return null else 0

        // Validate non-negative values
        if (major < 0 || minor < 0 || patch < 0) return null

        return Triple(major, minor, patch)
    }

    /**
     * Converts a semantic version string to a numeric version code.
     *
     * Formula: major * 1,000,000 + minor * 1,000 + patch
     *
     * Examples:
     * - "1.0.0" -> 1,000,000
     * - "1.2.3" -> 1,002,003
     * - "2.10.5" -> 2,010,005
     * - "1.0" -> 1,000,000 (missing patch treated as 0)
     *
     * This formula supports:
     * - Major: 0-999 (0 to 999,000,000)
     * - Minor: 0-999 (0 to 999,000)
     * - Patch: 0-999 (0 to 999)
     * - Max version code: 999,999,999 (within Int.MAX_VALUE of 2,147,483,647)
     *
     * @param version Version string (e.g., "1.2.3")
     * @return Version code or null if parsing fails or values exceed limits
     */
    fun versionToCode(version: String): Int? {
        val parsed = parseVersion(version) ?: return null
        val (major, minor, patch) = parsed

        // Validate component ranges to prevent overflow
        if (major > 999 || minor > 999 || patch > 999) return null

        return major * 1_000_000 + minor * 1_000 + patch
    }

    /**
     * Converts a numeric version code back to a semantic version string.
     *
     * @param code Version code (e.g., 1002003)
     * @return Version string (e.g., "1.2.3") or null if code is invalid
     */
    fun codeToVersion(code: Int): String? {
        if (code < 0) return null

        val major = code / 1_000_000
        val minor = (code % 1_000_000) / 1_000
        val patch = code % 1_000

        // Validate ranges
        if (major > 999 || minor > 999 || patch > 999) return null

        return "$major.$minor.$patch"
    }
}
