package org.zorel.gb3android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface PollEvent {
    data object Connecting : PollEvent
    data object Connected : PollEvent
    data class NewPost(val post: Post) : PollEvent
    data class Failed(val message: String) : PollEvent
}

open class Gb3Exception(message: String, cause: Throwable? = null) : IOException(message, cause)

class AttachmentTooLargeException :
    Gb3Exception("file is too large, maximum size is 4 MiB")

/**
 * HTTP and server-sent-events client for gb2c.
 */
class Gb3Api(
    private val client: OkHttpClient = defaultClient(),
) {
    private val sseClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun tribunes(baseUrl: String): List<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val response = execute(request("${baseUrl.trimEnd('/')}/gb2c/list"))
        response.use {
            requireSuccess(it)
            val array = JSONArray(it.body?.string().orEmpty())
            buildList {
                for (index in 0 until array.length()) {
                    val name = array.optString(index, "")
                    if (name.isNotBlank()) add(name)
                }
            }.sorted()
        }
    }

    suspend fun blockedIdentities(baseUrl: String): List<String> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = execute(request("${baseUrl.trimEnd('/')}/gb2c/config"))
            response.use {
                if (!it.isSuccessful) return@use emptyList()
                val array = JSONObject(it.body?.string().orEmpty())
                    .optJSONArray("blockedIdentities")
                    ?: return@use emptyList()
                buildList {
                    for (index in 0 until array.length()) {
                        val identity = array.optString(index, "")
                        if (identity.isNotBlank()) add(identity)
                    }
                }
            }
        }

    suspend fun post(
        baseUrl: String,
        tribune: String,
        info: String,
        message: String,
        accessToken: String? = null,
    ) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("tribune", tribune)
            .add("info", info)
            .add("message", message)
            .build()
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/gb2c/post")
            .post(body)
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        execute(builder.build()).use { response ->
            if (!response.isSuccessful) {
                throw Gb3Exception("post failed: HTTP ${response.code} ${response.message}")
            }
        }
    }

    suspend fun searchTotoz(baseUrl: String, terms: String): List<Totoz> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/gb2c/totoz/search?terms=${encodeQuery(terms)}"
            execute(request(url)).use { response ->
                requireSuccess(response)
                val root = JSONObject(response.body?.string().orEmpty())
                val array = root.optJSONArray("totozes") ?: JSONArray()
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString("name")
                        if (name.isNotBlank()) {
                            add(Totoz(name = name, image = item.optString("image")))
                        }
                    }
                }
            }
        }

    suspend fun searchEmoji(baseUrl: String, terms: String): List<EmojiItem> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/gb2c/emoji/search?terms=${encodeQuery(terms)}"
            execute(request(url)).use { response ->
                requireSuccess(response)
                val array = JSONArray(response.body?.string().orEmpty())
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val characters = item.optString("characters")
                        if (characters.isNotBlank()) {
                            add(EmojiItem(item.optString("name"), characters))
                        }
                    }
                }
            }
        }

    suspend fun uploadAttachment(
        baseUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val fileName = attachmentFileName(contentResolver, uri)
        val bytes = readAttachmentBytes(contentResolver, uri)
        val mediaType = (contentResolver.getType(uri) ?: "application/octet-stream").toMediaTypeOrNull()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("attachment", fileName, bytes.toRequestBody(mediaType))
            .build()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/gb2c/attachment/")
            .post(body)
            .build()
        execute(request).use { response ->
            when (response.code) {
                413 -> throw AttachmentTooLargeException()
                in 200..299 -> {
                    response.header("Location")?.takeIf { it.isNotBlank() }
                        ?: throw Gb3Exception("attachment uploaded but no URL was returned")
                }
                else -> throw Gb3Exception("attachment failed: HTTP ${response.code} ${response.message}")
            }
        }
    }

    suspend fun refreshLinuxfr(baseUrl: String, refreshToken: String): LinuxfrSession =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = FormBody.Builder().add("refresh_token", refreshToken).build()
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/gb2c/linuxfr/refresh")
                .post(body)
                .build()
            execute(request).use { response ->
                requireSuccess(response)
                val json = JSONObject(response.body?.string().orEmpty())
                val access = json.optString("access_token")
                if (access.isBlank()) throw Gb3Exception("linuxfr refresh did not return a token")
                LinuxfrSession(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token", refreshToken),
                    expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
                    login = "",
                )
            }
        }

    /**
     * Long-lived poll stream. gb2c closes each SSE connection after an hour, so
     * the loop transparently reconnects after every close and every failure.
     */
    fun poll(baseUrl: String, tribunes: List<String>): Flow<PollEvent> = flow {
        val pollUrl = "${baseUrl.trimEnd('/')}/gb2c/poll"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("filter", tribunes.joinToString(","))
            ?.build()
            ?: throw Gb3Exception("invalid server URL: $baseUrl")
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            emit(PollEvent.Connecting)
            var connected = false
            try {
                pollOnce(pollUrl).collect { event ->
                    if (event is PollEvent.Connected) {
                        connected = true
                        attempt = 0
                    }
                    emit(event)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(PollEvent.Failed(error.message ?: "connection lost"))
            }
            if (connected) attempt = 0
            attempt = (attempt + 1).coerceAtMost(5)
            delay((1000L shl (attempt - 1)).coerceAtMost(30_000L))
        }
    }

    private fun pollOnce(url: HttpUrl): Flow<PollEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Use blocking sends: gb2c replays its whole history in one
                // burst, and silently dropping events when the small callbackFlow
                // buffer is full would make posts disappear.
                trySendBlocking(PollEvent.Connected)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (type != null && type != "message") return
                val post = parsePost(data) ?: return
                trySendBlocking(PollEvent.NewPost(post))
            }

            override fun onClosed(eventSource: EventSource) {
                close(IOException("poll stream closed"))
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                close(IOException(t?.message ?: "poll stream failed", t))
            }
        }
        val source = EventSources.createFactory(sseClient).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun parsePost(data: String): Post? = runCatching {
        val json = JSONObject(data)
        Post(
            id = json.optLong("id"),
            time = json.optString("time"),
            info = json.optString("info"),
            login = json.optString("login"),
            message = json.optString("message"),
            tribune = json.optString("tribune"),
        )
    }.getOrNull()?.takeIf { it.tribune.isNotBlank() && it.id > 0 }

    private fun request(url: String): Request = Request.Builder().url(url).get().build()

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    private fun requireSuccess(response: Response) {
        if (!response.isSuccessful) {
            throw Gb3Exception("server returned HTTP ${response.code} ${response.message}")
        }
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun attachmentFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = uri.lastPathSegment ?: "attachment"
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                }
            }
        }
        return name.replace("\r", "").replace("\n", "").replace("\"", "").ifBlank { "attachment" }
    }

    private fun readAttachmentBytes(contentResolver: ContentResolver, uri: Uri): ByteArray {
        val maxSize = 4 * 1024 * 1024
        val output = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxSize) throw AttachmentTooLargeException()
                output.write(buffer, 0, count)
            }
        } ?: throw Gb3Exception("cannot open the selected file")
        return output.toByteArray()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun normalizeServerUrl(raw: String): String? {
            var value = raw.trim()
            if (value.isEmpty()) return null
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            val url = value.toHttpUrlOrNull() ?: return null
            return url.toString().trimEnd('/')
        }
    }
}
