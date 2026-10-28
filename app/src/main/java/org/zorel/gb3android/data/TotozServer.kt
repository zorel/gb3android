package org.zorel.gb3android.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A totoz search server, as used by gb2c.
 *
 * gb2c proxies `https://nsfw.totoz.eu/search.xml?terms=...` and builds image
 * URLs under `/img/<name>`. The app can talk to the same server directly so
 * totoz search keeps working in direct-board mode, or with a custom totoz
 * instance.
 */
object TotozServer {
    const val DEFAULT_URL = "https://nsfw.totoz.eu"

    fun normalizeUrl(raw: String): String? = Board.normalizeHttpUrl(raw)

    fun searchUrl(serverUrl: String, terms: String): HttpUrl? {
        val base = baseUrl(serverUrl) ?: return null
        return base.newBuilder()
            .addPathSegment("search.xml")
            .addQueryParameter("terms", terms)
            .build()
    }

    fun imageUrl(serverUrl: String, name: String): String? {
        val base = baseUrl(serverUrl) ?: return null
        return base.newBuilder()
            .addPathSegment("img")
            .addPathSegment(name)
            .build()
            .toString()
    }

    private fun baseUrl(serverUrl: String): HttpUrl? {
        val value = serverUrl.trim().trimEnd('/')
        if (value.isEmpty()) return null
        var url = value.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        // Accept both a base server URL and the full search.xml endpoint that
        // gb2c uses internally.
        if (url.encodedPath.endsWith("/search.xml")) {
            val path = url.encodedPath.removeSuffix("/search.xml").ifBlank { "/" }
            url = url.newBuilder().encodedPath(path).query(null).build()
        }
        return url
    }
}
