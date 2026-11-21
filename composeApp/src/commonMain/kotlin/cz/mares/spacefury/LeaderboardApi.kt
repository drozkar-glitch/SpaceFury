package cz.mares.spacefury

data class LeaderboardEntry(
    val key: String,
    val nick: String,
    val score: Int,
    val wave: Int,
    val pinHash: String = ""
)

object LeaderboardApi {
    private const val BASE_URL = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"
    private const val NODE = "spacefury_scores"

    fun normalizeKey(nick: String): String {
        val cleaned = buildString {
            for (ch in nick.trim().lowercase()) {
                when {
                    ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                    ch == 'á' -> append('a')
                    ch == 'č' -> append('c')
                    ch == 'ď' -> append('d')
                    ch == 'é' || ch == 'ě' -> append('e')
                    ch == 'í' -> append('i')
                    ch == 'ň' -> append('n')
                    ch == 'ó' -> append('o')
                    ch == 'ř' -> append('r')
                    ch == 'š' -> append('s')
                    ch == 'ť' -> append('t')
                    ch == 'ú' || ch == 'ů' -> append('u')
                    ch == 'ý' -> append('y')
                    ch == 'ž' -> append('z')
                    ch == ' ' || ch == '_' || ch == '-' -> append('_')
                }
            }
        }.trim('_')
        return cleaned.take(24)
    }

    fun hashPin(nick: String, pin: String): String {
        val raw = "${normalizeKey(nick)}:${pin.trim()}:sf1981"
        var h = -3750763034362895579L
        for (b in raw.encodeToByteArray()) {
            h = h xor (b.toLong() and 0xFFL)
            h *= 1099511628211L
        }
        return h.toULong().toString(16).padStart(16, '0')
    }

    suspend fun fetchTop50(net: PlatformNetStorage): List<LeaderboardEntry> {
        val res = net.httpRequest("GET", "$BASE_URL/$NODE.json")
        if (res.code !in 200..299 || res.body.isBlank() || res.body.trim() == "null") {
            return emptyList()
        }
        return parseEntriesMap(res.body)
            .filter { it.score > 0 && it.nick.isNotBlank() }
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenByDescending { it.wave })
            .take(50)
    }

    suspend fun loginOrRegister(
        net: PlatformNetStorage,
        nick: String,
        pin: String,
        localBestScore: Int,
        localBestWave: Int
    ): Result<LeaderboardEntry> {
        val cleanNick = nick.trim().take(20)
        val key = normalizeKey(cleanNick)
        if (key.length < 2) {
            return Result.failure(IllegalArgumentException("Zadej přezdívku alespoň o 2 znacích."))
        }
        if (pin.trim().length < 4) {
            return Result.failure(IllegalArgumentException("PIN nebo heslo musí mít alespoň 4 znaky."))
        }

        val expectedHash = hashPin(cleanNick, pin)
        val existingRes = net.httpRequest("GET", "$BASE_URL/$NODE/$key.json")
        if (existingRes.code == 0) {
            return Result.failure(IllegalStateException("Nelze se spojit se serverem žebříčku."))
        }

        val existing = if (existingRes.body.isNotBlank() && existingRes.body.trim() != "null") {
            parseSingleObject(key, existingRes.body)
        } else {
            null
        }

        if (existing != null && existing.pinHash.isNotBlank() && existing.pinHash != expectedHash) {
            return Result.failure(IllegalArgumentException("Tato přezdívka je již obsazená jiným PINem."))
        }

        val mergedScore = maxOf(localBestScore, existing?.score ?: 0)
        val mergedWave = maxOf(localBestWave, existing?.wave ?: 1)
        val entry = LeaderboardEntry(
            key = key,
            nick = existing?.nick?.ifBlank { cleanNick } ?: cleanNick,
            score = mergedScore,
            wave = mergedWave,
            pinHash = expectedHash
        )
        val putRes = net.httpRequest("PUT", "$BASE_URL/$NODE/$key.json", toJson(entry))
        if (putRes.code !in 200..299) {
            return Result.failure(IllegalStateException("Uložení účtu se nezdařilo (HTTP ${putRes.code})."))
        }
        return Result.success(entry)
    }

    suspend fun submitScoreIfHigher(
        net: PlatformNetStorage,
        nick: String,
        pin: String,
        score: Int,
        wave: Int
    ) {
        if (score <= 0 || nick.isBlank() || pin.isBlank()) return
        val key = normalizeKey(nick)
        if (key.length < 2) return
        val pinHash = hashPin(nick, pin)

        val currentRes = net.httpRequest("GET", "$BASE_URL/$NODE/$key.json")
        val current = if (currentRes.code in 200..299 && currentRes.body.trim() != "null") {
            parseSingleObject(key, currentRes.body)
        } else {
            null
        }
        if (current != null && current.pinHash.isNotBlank() && current.pinHash != pinHash) return
        if (current != null && current.score >= score) return

        val updated = LeaderboardEntry(
            key = key,
            nick = current?.nick?.ifBlank { nick.trim() } ?: nick.trim(),
            score = score,
            wave = maxOf(wave, current?.wave ?: 1),
            pinHash = pinHash
        )
        net.httpRequest("PUT", "$BASE_URL/$NODE/$key.json", toJson(updated))
    }

    suspend fun deleteAccount(net: PlatformNetStorage, nick: String, pin: String): Boolean {
        val key = normalizeKey(nick)
        if (key.isBlank()) return false
        val expectedHash = hashPin(nick, pin)
        val existingRes = net.httpRequest("GET", "$BASE_URL/$NODE/$key.json")
        if (existingRes.code in 200..299 && existingRes.body.trim() != "null") {
            val parsed = parseSingleObject(key, existingRes.body)
            if (parsed != null && parsed.pinHash.isNotBlank() && parsed.pinHash != expectedHash) {
                return false
            }
        }
        val del = net.httpRequest("DELETE", "$BASE_URL/$NODE/$key.json")
        return del.code in 200..299
    }

    private fun toJson(e: LeaderboardEntry): String {
        val safeNick = e.nick.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"nick":"$safeNick","score":${e.score},"wave":${e.wave},"pinHash":"${e.pinHash}"}"""
    }

    private fun parseEntriesMap(json: String): List<LeaderboardEntry> {
        val result = mutableListOf<LeaderboardEntry>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return result
        var i = 1
        while (i < trimmed.length) {
            val kStart = trimmed.indexOf('"', i)
            if (kStart < 0) break
            val kEnd = findClosingQuote(trimmed, kStart + 1)
            if (kEnd < 0) break
            val key = trimmed.substring(kStart + 1, kEnd)
            val objStart = trimmed.indexOf('{', kEnd)
            if (objStart < 0) break
            val objEnd = findMatchingBrace(trimmed, objStart)
            if (objEnd < 0) break
            val objStr = trimmed.substring(objStart, objEnd + 1)
            parseSingleObject(key, objStr)?.let { result.add(it) }
            i = objEnd + 1
        }
        return result
    }

    private fun parseSingleObject(key: String, obj: String): LeaderboardEntry? {
        val nick = extractJsonString(obj, "nick") ?: return null
        val score = extractJsonInt(obj, "score") ?: 0
        val wave = extractJsonInt(obj, "wave") ?: 1
        val pinHash = extractJsonString(obj, "pinHash") ?: ""
        return LeaderboardEntry(key = key, nick = nick, score = score, wave = wave, pinHash = pinHash)
    }

    private fun extractJsonString(obj: String, field: String): String? {
        val token = "\"$field\""
        val pos = obj.indexOf(token)
        if (pos < 0) return null
        val colon = obj.indexOf(':', pos + token.length)
        if (colon < 0) return null
        val qStart = obj.indexOf('"', colon + 1)
        if (qStart < 0) return null
        val qEnd = findClosingQuote(obj, qStart + 1)
        if (qEnd < 0) return null
        return obj.substring(qStart + 1, qEnd).replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractJsonInt(obj: String, field: String): Int? {
        val token = "\"$field\""
        val pos = obj.indexOf(token)
        if (pos < 0) return null
        val colon = obj.indexOf(':', pos + token.length)
        if (colon < 0) return null
        var s = colon + 1
        while (s < obj.length && (obj[s].isWhitespace() || obj[s] == '"')) s++
        var e = s
        while (e < obj.length && (obj[e].isDigit() || obj[e] == '-')) e++
        return if (e > s) obj.substring(s, e).toIntOrNull() else null
    }

    private fun findClosingQuote(s: String, from: Int): Int {
        var idx = from
        while (idx < s.length) {
            if (s[idx] == '"' && s[idx - 1] != '\\') return idx
            idx++
        }
        return -1
    }

    private fun findMatchingBrace(s: String, openIdx: Int): Int {
        var depth = 0
        var inStr = false
        for (i in openIdx until s.length) {
            val c = s[i]
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) {
                inStr = !inStr
            } else if (!inStr) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
