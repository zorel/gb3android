package org.zorel.gb3android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Direct client for a remote bouchot board.
 *
 * This is the Android equivalent of gb2c's `TribuneClient`: it downloads the
 * TSV backend and posts form-encoded messages without going through a gb2c
 * server.
 */
class BoardClient(
    private val client: OkHttpClient = Gb3Api.defaultClient(),
) {
    suspend fun fetch(board: Board): List<Post> = withContext(Dispatchers.IO) {
        board.validationError()?.let { throw Gb3Exception("invalid board: $it") }
        val request = Request.Builder()
            .url(board.backendUrl)
            .header("Accept", "text/tab-separated-values, text/plain, */*")
            .get()
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw Gb3Exception(
                    "cannot read ${board.name}: HTTP ${response.code} ${response.message}",
                )
            }
            BoardTsv.parse(readBody(response), board.name)
        }
    }

    suspend fun post(
        board: Board,
        info: String,
        message: String,
        accessToken: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        board.validationError()?.let { throw Gb3Exception("invalid board: $it") }
        val safeMessage = message.trim()
        if (safeMessage.isEmpty()) throw Gb3Exception("message is empty")
        if (safeMessage.length > BoardTsv.MAX_MESSAGE_LENGTH) {
            throw Gb3Exception("message is too long")
        }
        val body = FormBody.Builder()
            .add(board.postField, safeMessage)
            .apply {
                if (board.infoField.isNotBlank()) add(board.infoField, info)
            }
            .build()
        val builder = Request.Builder()
            .url(board.postUrl)
            .post(body)
            .header("User-Agent", "gb3android")
        if (board.infoField.isBlank()) {
            // gb2c uses the User-Agent header as the identity when the board
            // has no dedicated info field. Header values cannot contain CR/LF.
            val identity = info
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .ifBlank { "gb3android" }
            builder.header("User-Agent", identity)
        }
        if (board.requiresOAuth) {
            val token = accessToken?.trim().orEmpty()
            if (token.isEmpty()) {
                throw Gb3Exception("${board.name} requires a LinuxFR login")
            }
            builder.header("Authorization", "Bearer $token")
        }
        execute(builder.build()).use { response ->
            if (!response.isSuccessful) {
                throw Gb3Exception(
                    "post to ${board.name} failed: HTTP ${response.code} ${response.message}",
                )
            }
        }
    }

    private fun readBody(response: Response): String {
        val body = response.body ?: return ""
        if (body.contentLength() > MAX_BACKEND_BYTES) {
            throw Gb3Exception("board response is too large")
        }
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BACKEND_BYTES) {
                    throw Gb3Exception("board response is too large")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
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

    private companion object {
        const val MAX_BACKEND_BYTES = 8 * 1024 * 1024
    }
}
