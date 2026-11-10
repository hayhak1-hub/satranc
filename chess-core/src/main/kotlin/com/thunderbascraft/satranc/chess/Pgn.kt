package com.thunderbascraft.satranc.chess

/** A game read from (or written to) PGN, with its tag pairs. */
data class PgnGame(val tags: Map<String, String>, val game: Game) {
    val white: String? get() = tags["White"]?.takeUnless { it.isBlank() || it == "?" }
    val black: String? get() = tags["Black"]?.takeUnless { it.isBlank() || it == "?" }
    val event: String? get() = tags["Event"]?.takeUnless { it.isBlank() || it == "?" }
    val date: String? get() = tags["Date"]?.takeUnless { it.isBlank() || it.startsWith("?") }
    /** The Result tag, falling back to the result reached on the board. */
    val result: String get() = tags["Result"]?.takeIf { it in RESULTS } ?: game.result?.pgn ?: "*"

    companion object {
        val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
    }
}

/** Why a PGN could not be read. [moveLabel] is e.g. "23. Nf7" or "23... Nf7". */
class PgnException(val kind: Kind, val moveLabel: String? = null) : Exception(
    when (kind) {
        Kind.NO_GAME -> "No game found"
        Kind.BAD_FEN -> "Invalid FEN"
        Kind.ILLEGAL_MOVE -> "Illegal move $moveLabel"
    }
) {
    enum class Kind { NO_GAME, BAD_FEN, ILLEGAL_MOVE }
}

/** Reading and writing PGN (Portable Game Notation). */
object Pgn {
    private val TAG = Regex("""^\s*\[\s*(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]\s*$""")
    private val MOVE_NUMBER = Regex("""^\d+\.+""")
    private val SUFFIX = Regex("""[!?]+$""")
    private val EP = Regex("""e\.p\.$""")

    /** Splits a PGN file into the raw text of each game; BOM and Windows line endings are fine. */
    fun splitGames(text: String): List<String> {
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val games = ArrayList<String>()
        val current = StringBuilder()
        var hasMoves = false
        var inComment = false
        for (line in lines) {
            val isTag = !inComment && TAG.matches(line)
            if (isTag && hasMoves) {
                games.add(current.toString()); current.clear(); hasMoves = false
            }
            if (!isTag && !inComment && line.isNotBlank() && !line.trimStart().startsWith("%")) hasMoves = true
            // Track multi-line {comments} so that a "[" inside them does not start a new game.
            for (c in line) {
                if (!inComment && c == '{') inComment = true else if (inComment && c == '}') inComment = false
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) games.add(current.toString())
        return games.filter { g -> g.lines().any { it.isNotBlank() } }
    }

    /** Parses every game; throws [PgnException] for the first game that cannot be read. */
    fun parseAll(text: String): List<PgnGame> {
        val raw = splitGames(text)
        if (raw.isEmpty()) throw PgnException(PgnException.Kind.NO_GAME)
        return raw.map(::parseGame)
    }

    /** Only the tags of each game (cheap, for choosing a game from a large file). */
    fun headers(text: String): List<Map<String, String>> = splitGames(text).map(::parseTags)

    private fun parseTags(raw: String): Map<String, String> {
        val tags = LinkedHashMap<String, String>()
        for (line in raw.lineSequence()) {
            val m = TAG.matchEntire(line) ?: continue
            tags[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return tags
    }

    fun parseGame(raw: String): PgnGame {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val tags = parseTags(text)
        val fen = tags["FEN"]
        val game = try {
            Game(fen ?: Position.START_FEN)
        } catch (e: RuntimeException) {
            throw PgnException(PgnException.Kind.BAD_FEN)
        }
        val movetext = text.lineSequence().filterNot { TAG.matches(it) || it.trimStart().startsWith("%") }
            .joinToString("\n")
        for (token in movetextTokens(movetext)) {
            val san = cleanToken(token) ?: continue
            if (!game.playSan(san)) {
                val pos = game.position
                val label = if (pos.sideToMove == Side.WHITE) "${pos.fullmoveNumber}. $san" else "${pos.fullmoveNumber}... $san"
                throw PgnException(PgnException.Kind.ILLEGAL_MOVE, label)
            }
        }
        if (game.moves.isEmpty() && tags.isEmpty()) throw PgnException(PgnException.Kind.NO_GAME)
        return PgnGame(tags, game)
    }

    /** Main-line tokens: comments, ; comments, (nested variations) and $NAGs removed. */
    internal fun movetextTokens(movetext: String): List<String> {
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        fun flush() { if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() } }
        while (i < movetext.length) {
            val c = movetext[i]
            when {
                c == '{' -> { flush(); val end = movetext.indexOf('}', i); i = if (end < 0) movetext.length else end }
                c == ';' -> { flush(); val end = movetext.indexOf('\n', i); i = if (end < 0) movetext.length else end }
                c == '(' -> { flush(); depth++ }
                c == ')' -> { flush(); if (depth > 0) depth-- }
                depth > 0 -> {}
                c.isWhitespace() -> flush()
                else -> sb.append(c)
            }
            i++
        }
        flush()
        return tokens
    }

    /** Turns a token into a SAN move, or null for move numbers, results, NAGs and the like. */
    private fun cleanToken(token: String): String? {
        var t = token.trim()
        if (t.isEmpty() || t.startsWith("$") || t in PgnGame.RESULTS) return null
        t = t.replace(MOVE_NUMBER, "")
        if (t.isEmpty() || t == "e.p.") return null
        t = t.replace(EP, "").replace(SUFFIX, "").trimEnd('+', '#').replace(SUFFIX, "")
        if (t.isEmpty() || t == "--") return null
        return t
    }

    /** Writes [game] as PGN; [tags] fill the seven-tag roster (missing ones become "?"). */
    fun write(game: Game, tags: Map<String, String> = emptyMap()): String = buildString {
        val res = game.result?.pgn ?: tags["Result"]?.takeIf { it in PgnGame.RESULTS } ?: "*"
        val roster = linkedMapOf(
            "Event" to (tags["Event"] ?: "?"),
            "Site" to (tags["Site"] ?: "?"),
            "Date" to (tags["Date"] ?: "????.??.??"),
            "Round" to (tags["Round"] ?: "-"),
            "White" to (tags["White"] ?: "?"),
            "Black" to (tags["Black"] ?: "?"),
            "Result" to res,
        )
        val start = game.startPosition.toFen()
        val extra = tags.filterKeys { it !in roster && it != "SetUp" && it != "FEN" }
        for ((k, v) in roster + extra) appendLine("[$k \"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\"]")
        if (start != Position.START_FEN) {
            appendLine("[SetUp \"1\"]")
            appendLine("[FEN \"$start\"]")
        }
        appendLine()
        val tokens = mutableListOf<String>()
        var moveNo = game.startPosition.fullmoveNumber
        var side = game.startPosition.sideToMove
        for ((i, pm) in game.moves.withIndex()) {
            if (side == Side.WHITE) tokens.add("$moveNo.") else if (i == 0) tokens.add("$moveNo...")
            tokens.add(pm.san)
            if (side == Side.BLACK) moveNo++
            side = side.opposite
        }
        tokens.add(res)
        var line = StringBuilder()
        for (t in tokens) {
            if (line.isNotEmpty() && line.length + 1 + t.length > 80) { appendLine(line); line = StringBuilder() }
            if (line.isNotEmpty()) line.append(' ')
            line.append(t)
        }
        appendLine(line)
    }
}
