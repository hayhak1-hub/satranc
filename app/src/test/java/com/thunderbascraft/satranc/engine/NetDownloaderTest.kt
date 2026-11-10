package com.thunderbascraft.satranc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NetDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val content = ByteArray(300_000) { (it * 31 + 7).toByte() }
    private fun source(bytes: ByteArray = content): File = tmp.newFile().apply { writeBytes(bytes) }
    private fun shaOf(bytes: ByteArray) = NetDownloader.sha256Of(source(bytes))

    @Test fun sha256OfKnownValue() {
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            NetDownloader.sha256Of(source("abc".toByteArray())),
        )
    }

    @Test fun downloadsAndVerifies() {
        val target = File(tmp.root, "nets/net.nnue")
        var last = 0L
        val result = NetDownloader(content.size.toLong(), shaOf(content))
            .download(listOf(source().toURI().toString()), target, onProgress = { d, _ -> last = d })
        assertEquals(DownloadResult.Ok, result)
        assertTrue(target.readBytes().contentEquals(content))
        assertEquals(content.size.toLong(), last)
        assertFalse(File(target.path + ".part").exists())
        assertTrue(NetDownloader.verify(target, content.size.toLong(), shaOf(content)))
    }

    @Test fun rejectsWrongChecksumAndKeepsNoFile() {
        val target = File(tmp.root, "net.nnue")
        val result = NetDownloader(content.size.toLong(), "00".repeat(32))
            .download(listOf(source().toURI().toString()), target)
        assertTrue(result is DownloadResult.Failed)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test fun rejectsWrongSize() {
        val target = File(tmp.root, "net.nnue")
        val result = NetDownloader(content.size + 1L, shaOf(content))
            .download(listOf(source().toURI().toString()), target)
        assertTrue(result is DownloadResult.Failed)
        assertFalse(target.exists())
    }

    @Test fun fallsBackToSecondUrl() {
        val target = File(tmp.root, "net.nnue")
        val missing = File(tmp.root, "does-not-exist").toURI().toString()
        val result = NetDownloader(content.size.toLong(), shaOf(content))
            .download(listOf(missing, source().toURI().toString()), target)
        assertEquals(DownloadResult.Ok, result)
        assertTrue(target.exists())
    }

    @Test fun cancelStopsAndCleansUp() {
        val target = File(tmp.root, "net.nnue")
        var calls = 0
        val result = NetDownloader(content.size.toLong(), shaOf(content))
            .download(listOf(source().toURI().toString()), target, isCancelled = { ++calls > 2 })
        assertEquals(DownloadResult.Cancelled, result)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test fun verifyDetectsCorruption() {
        val f = source()
        val sha = shaOf(content)
        assertTrue(NetDownloader.verify(f, content.size.toLong(), sha))
        f.writeBytes(content.copyOf().also { it[10] = (it[10] + 1).toByte() })
        assertFalse(NetDownloader.verify(f, content.size.toLong(), sha))
    }
}
