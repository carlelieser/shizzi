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

/** How far a download has got, for the card to render. */
data class DownloadProgress(val bytesRead: Long, val totalBytes: Long)

/**
 * Why a download did not produce a verified file.
 *
 * [isConnectivity] separates the one failure a user can fix by joining a network
 * from every other: a 404 and a digest mismatch are also failures, and offering
 * "check your connection" for either sends the user after the wrong thing. The
 * verbatim [reason] is carried under both (R7.5) — this decides the headline.
 */
data class DownloadFailure(val reason: String, val isConnectivity: Boolean)

/**
 * Fetches the tethering APEX into app-private storage and proves it intact.
 *
 * HttpURLConnection rather than a client library: this is one GET, and the
 * project carries no HTTP dependency to reuse. INTERNET is already declared.
 *
 * Runs in the app process. The shell process does the install — it can write
 * /data/local/tmp, which this one cannot — so the file crosses as a path.
 */
class ApexDownloader(private val context: Context) {

    /**
     * Streams to disk while digesting, so the bytes are never held whole in
     * memory and the check covers exactly what was written.
     *
     * @param onProgress called on the calling thread, per chunk.
     * @return the verified file, or why there is not one.
     */
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

    /** @return the SHA-256 of everything written to [sink]. */
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

    /**
     * Size first, because a truncated body names the shorter problem; the digest
     * would only report that the bytes differ.
     */
    private fun verify(destination: File, digest: String) {
        val size = destination.length()
        check(size == TetheringApex.SIZE_BYTES) {
            "downloaded $size bytes, expected ${TetheringApex.SIZE_BYTES}"
        }
        check(digest == TetheringApex.SHA_256) {
            "SHA-256 was $digest, expected ${TetheringApex.SHA_256}"
        }
    }

    /**
     * No route versus anything else.
     *
     * These two are what a device with no validated network raises; a bad status
     * or a digest mismatch reaches here as something else and keeps its own
     * headline.
     */
    private fun isConnectivity(failure: Throwable): Boolean =
        failure is UnknownHostException || failure is SocketTimeoutException

    private fun describe(failure: Throwable): String =
        "${failure.javaClass.simpleName}: ${failure.message}"

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_MS = 30_000
    }
}

/** Carries [DownloadFailure] out of the [Result] the download returns. */
class ApexDownloadException(
    val reason: String,
    val isConnectivity: Boolean,
    cause: Throwable,
) : Exception(reason, cause)

/** The failure a [Result] from [ApexDownloader.download] carries. */
fun Throwable.asDownloadFailure(): DownloadFailure = when (this) {
    is ApexDownloadException -> DownloadFailure(reason, isConnectivity)
    else -> DownloadFailure("${javaClass.simpleName}: $message", isConnectivity = false)
}
