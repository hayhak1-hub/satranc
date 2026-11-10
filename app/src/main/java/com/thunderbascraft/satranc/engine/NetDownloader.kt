package com.thunderbascraft.satranc.engine

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The NNUE network used by Stockfish 19. It is data, not code, so it may be downloaded. */
object NetFile {
    const val NAME = "nn-1a298aa575a0.nnue"
    const val SIZE = 98_511_183L
    const val SHA256 = "1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2"
    val URLS = listOf(
        "https://tests.stockfishchess.org/api/nn/$NAME",
        "https://github.com/official-stockfish/networks/raw/master/$NAME",
    )
}

sealed interface DownloadResult {
    data object Ok : DownloadResult
    data object Cancelled : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

/**
 * Downloads a file from the first working URL to [target], checking its size and SHA-256.
 * Pure JVM (works with any `URLConnection`, including `file:` URLs in tests). The file only
 * appears at [target] once it has been verified.
 */
class NetDownloader(private val expectedSize: Long, private val expectedSha256: String) {

    fun download(
        urls: List<String>,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): DownloadResult {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        var lastError = "no URL"
        for (url in urls) {
            try {
                val result = downloadOne(url, tmp, onProgress, isCancelled)
                if (result != null) {
                    tmp.delete()
                    if (result is DownloadResult.Cancelled) return result
                    lastError = (result as DownloadResult.Failed).reason
                    continue
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return DownloadResult.Failed("could not save file")
                }
                return DownloadResult.Ok
            } catch (e: IOException) {
                tmp.delete()
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        return DownloadResult.Failed(lastError)
    }

    /** Returns null on success (verified file in [tmp]), otherwise the failure or cancellation. */
    private fun downloadOne(
        url: String,
        tmp: File,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult? {
        val conn = URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ThunderChess-Android")
        }
        if (conn is HttpURLConnection && conn.responseCode !in 200..299) {
            conn.disconnect()
            return DownloadResult.Failed("HTTP ${conn.responseCode}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        try {
            conn.getInputStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) return DownloadResult.Cancelled
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done > expectedSize) return DownloadResult.Failed("file too large")
                        onProgress(done, expectedSize)
                    }
                }
            }
        } finally {
            (conn as? HttpURLConnection)?.disconnect()
        }
        if (done != expectedSize) return DownloadResult.Failed("wrong size: $done")
        val sha = digest.digest().toHex()
        if (!sha.equals(expectedSha256, ignoreCase = true)) return DownloadResult.Failed("checksum mismatch")
        return null
    }

    companion object {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().toHex()
        }

        fun verify(file: File, size: Long, sha256: String): Boolean =
            file.isFile && file.length() == size && sha256Of(file).equals(sha256, ignoreCase = true)

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
