package com.taizi.domain.model

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) {
    companion object {
        fun fromString(version: String): SemanticVersion {
            val cleanVersion = version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .split("-").first()
            val parts = cleanVersion.split(".")
            if (parts.size < 3) throw IllegalArgumentException("Invalid semantic version: $version")
            val major = parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid major")
            val minor = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid minor")
            val patch = parts[2].toIntOrNull() ?: throw IllegalArgumentException("Invalid patch")
            return SemanticVersion(major, minor, patch)
        }
    }

    fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString() = "$major.$minor.$patch"
}