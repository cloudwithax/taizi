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
            val preRelease = cleanVersion.indexOf('-').let { idx ->
                if (idx >= 0) cleanVersion.substring(idx + 1) else null
            }
            val numericPart = cleanVersion.split("-").first()
            val parts = numericPart.split(".")
            if (parts.size < 3) throw IllegalArgumentException("Invalid semantic version: $version")
            val major = parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid major")
            val minor = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid minor")
            val patch = parts[2].toIntOrNull() ?: throw IllegalArgumentException("Invalid patch")
            return SemanticVersion(major, minor, patch, preRelease)
        }
    }

    fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // A version without pre-release has higher precedence than one with pre-release
        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease == null && other.preRelease == null) return 0

        // Compare pre-release identifiers dot-separated, numerically when possible
        val thisParts = preRelease!!.split(".")
        val otherParts = other.preRelease!!.split(".")
        val maxLen = maxOf(thisParts.size, otherParts.size)
        for (i in 0 until maxLen) {
            if (i >= thisParts.size) return -1
            if (i >= otherParts.size) return 1
            val thisId = thisParts[i]
            val otherId = otherParts[i]
            val thisNum = thisId.toIntOrNull()
            val otherNum = otherId.toIntOrNull()
            val cmp = when {
                thisNum != null && otherNum != null -> thisNum.compareTo(otherNum)
                thisNum != null -> -1 // numeric has lower precedence than alphanumeric
                otherNum != null -> 1
                else -> thisId.compareTo(otherId)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun toString(): String {
        return if (preRelease != null) "$major.$minor.$patch-$preRelease" else "$major.$minor.$patch"
    }
}