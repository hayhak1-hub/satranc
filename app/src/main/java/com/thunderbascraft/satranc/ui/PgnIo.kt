package com.thunderbascraft.satranc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.PgnException
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val PGN_MIME = "application/x-chess-pgn"

/** MIME types accepted when opening a PGN file (many apps report .pgn files as text or binary). */
val PGN_OPEN_TYPES = arrayOf(PGN_MIME, "application/vnd.chess-pgn", "text/plain", "application/octet-stream", "*/*")

/** File name like thunderchess-2026-11-10-1430.pgn. */
fun pgnFileName(): String =
    "thunderchess-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".pgn"

/** Shares [pgn] as a real .pgn file (FileProvider), with the text as a fallback. */
fun sharePgn(context: Context, pgn: String) {
    val dir = File(context.cacheDir, "pgn").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, pgnFileName()).apply { writeText(pgn) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(PGN_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, pgn)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Remembers a "Save PGN…" launcher (system file picker). Call `launch(pgnFileName())` after
 * setting the text with the returned setter.
 */
class PgnSaver(private val launcher: ManagedActivityResultLauncher<String, Uri?>, private val setText: (String) -> Unit) {
    fun save(pgn: String) {
        setText(pgn)
        launcher.launch(pgnFileName())
    }
}

@Composable
fun rememberPgnSaver(): PgnSaver {
    val context = LocalContext.current
    var pending by remember { mutableStateOf("") }
    val saved = stringResource(R.string.saved)
    val failed = stringResource(R.string.save_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PGN_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(pending.toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (ok) saved else failed, Toast.LENGTH_SHORT).show()
    }
    return remember(launcher) { PgnSaver(launcher) { pending = it } }
}

/** Reads a text document (PGN) from [uri]; at most 5 MB. */
fun readPgnText(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buf = input.readNBytesCompat(5 * 1024 * 1024 + 1)
        if (buf.size > 5 * 1024 * 1024) throw IOException("file too large")
        buf
    } ?: throw IOException("cannot open")
    return String(bytes, Charsets.UTF_8)
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** The clipboard text if it looks like PGN, else "". */
fun clipboardPgn(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    return if (Regex("""\[\s*\w+\s+"|(^|\s)1\.\s*[a-hNBRQKO]""").containsMatchIn(text)) text else ""
}

/** Localized message for a PGN import error. */
fun pgnErrorText(context: Context, e: Throwable): String = when (e) {
    is PgnException -> when (e.kind) {
        PgnException.Kind.NO_GAME -> context.getString(R.string.pgn_error_empty)
        PgnException.Kind.BAD_FEN -> context.getString(R.string.pgn_error_fen)
        PgnException.Kind.ILLEGAL_MOVE -> context.getString(R.string.pgn_error_illegal, e.moveLabel ?: "")
    }
    else -> context.getString(R.string.pgn_error_read)
}
