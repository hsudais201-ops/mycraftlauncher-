package com.mf.minecraftlauncher

import org.json.JSONObject

/** Small pure helpers kept separate so JVM unit tests can exercise safety rules. */
object LauncherLogic {
    const val MIN_RAM_MB = 512
    const val MAX_RAM_MB = 1024
    const val RAM_STEP_MB = 64

    fun normalizeRamMb(value: Int): Int {
        val bounded = value.coerceIn(MIN_RAM_MB, MAX_RAM_MB)
        val step = ((bounded - MIN_RAM_MB + RAM_STEP_MB / 2) / RAM_STEP_MB)
        return (MIN_RAM_MB + step * RAM_STEP_MB).coerceIn(MIN_RAM_MB, MAX_RAM_MB)
    }

    fun isValidMinecraftUsername(value: String): Boolean =
        value.matches(Regex("^[A-Za-z0-9_]{1,16}$"))

    /** Accepts host, host:port, IPv4, or bracketed IPv6 such as [2001:db8::1]:25565. */
    fun isValidServerAddress(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 300 || value.any { it.isWhitespace() }) return false

        val bracketedIpv6 = Regex("^\\[[0-9A-Fa-f:.]{2,45}](:([0-9]{1,5}))?$").matchEntire(value)
        if (bracketedIpv6 != null) {
            val portText = bracketedIpv6.groupValues.getOrNull(2).orEmpty()
            if (portText.isEmpty()) return true
            val port = portText.toIntOrNull() ?: return false
            return port in 1..65535
        }

        if (value.count { it == ':' } > 1) return false // IPv6 must use brackets.
        val host: String
        if (':' in value) {
            host = value.substringBeforeLast(':')
            val portText = value.substringAfterLast(':')
            if (portText.isEmpty() || portText.any { !it.isDigit() }) return false
            val port = portText.toIntOrNull() ?: return false
            if (port !in 1..65535) return false
        } else {
            host = value
        }

        if (host.length > 253 || host.startsWith('.') || host.endsWith('.') || ".." in host) return false
        val labels = host.split('.')
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 ||
                    !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                    label.any { !(it.isLetterOrDigit() || it == '-') }
            }) return false
        return true
    }

    fun parseVersionManifest(json: String): List<MinecraftVersion> {
        val root = JSONObject(json)
        val array = root.getJSONArray("versions")
        val result = ArrayList<MinecraftVersion>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id", "")
            val type = item.optString("type", "")
            val url = item.optString("url", "")
            if (!id.matches(Regex("^[A-Za-z0-9._+-]{1,100}$"))) continue
            if (type !in setOf("release", "snapshot", "old_beta", "old_alpha")) continue
            if (!url.startsWith("https://piston-meta.mojang.com/")) continue
            result.add(MinecraftVersion(id = id, type = type, metadataUrl = url))
        }
        return result
    }
}

data class MinecraftVersion(
    val id: String,
    val type: String,
    val metadataUrl: String
) {
    override fun toString(): String = "$id  •  $type"
}
