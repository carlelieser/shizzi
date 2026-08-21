package dev.shizzi

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

data class DownloadProgress(val bytesRead: Long, val totalBytes: Long)

data class DownloadFailure(val reason: String, val isConnectivity: Boolean)

class ApexDownloader(private val context: Context) {

    fun download(onProgress: (DownloadProgress) -> Unit): Result<File> {
        val destination = File(context.filesDir, TetheringApex.FILE_NAME)

        return runCatching { fetch(destination, onProgress) }
            .recoverCatching { failure ->
                destination.delete()
                throw ApexDownloadException(describe(failure), isConnectivity(failure), failure)
            }
    }

    private fun fetch(destination: File, onProgress: (DownloadProgress) -> Unit): File {
        val connection = openConnection()

        try {
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) {
                "GET ${TetheringApex.URL} returned HTTP $status"
            }

            val digest = connection.inputStream.use { body ->
                destination.outputStream().use { sink -> copyDigesting(body, sink, onProgress) }
            }

            verify(destination, digest)
        } finally {
            connection.disconnect()
        }

        return destination
    }

    private fun openConnection(): HttpURLConnection =
        (URL(TetheringApex.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private fun copyDigesting(
        source: InputStream,
        sink: OutputStream,
        onProgress: (DownloadProgress) -> Unit,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L

        while (true) {
            val count = source.read(buffer)
            if (count < 0) break

            sink.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            written += count
            onProgress(DownloadProgress(written, TetheringApex.SIZE_BYTES))
        }

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun verify(destination: File, digest: String) {
        val size = destination.length()
        check(size == TetheringApex.SIZE_BYTES) {
            "downloaded $size bytes, expected ${TetheringApex.SIZE_BYTES}"
        }
        check(digest == TetheringApex.SHA_256) {
            "SHA-256 was $digest, expected ${TetheringApex.SHA_256}"
        }
    }

    private fun isConnectivity(failure: Throwable): Boolean =
        failure is UnknownHostException || failure is SocketTimeoutException

    private fun describe(failure: Throwable): String =
        "${failure.javaClass.simpleName}: ${failure.message}"

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_MS = 30_000
    }
}

class ApexDownloadException(
    val reason: String,
    val isConnectivity: Boolean,
    cause: Throwable,
) : Exception(reason, cause)

fun Throwable.asDownloadFailure(): DownloadFailure = when (this) {
    is ApexDownloadException -> DownloadFailure(reason, isConnectivity)
    else -> DownloadFailure("${javaClass.simpleName}: $message", isConnectivity = false)
}
