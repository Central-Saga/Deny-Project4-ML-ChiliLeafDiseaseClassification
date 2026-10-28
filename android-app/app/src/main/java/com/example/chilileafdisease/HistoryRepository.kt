package com.example.chilileafdisease

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

/**
 * Model data bersama untuk satu catatan riwayat deteksi penyakit daun cabai.
 */
data class HistoryRecord(
    val label: String,
    val confidence: Double,
    val timestamp: Long,
    val photoFileName: String = ""
)

/**
 * Repository tunggal (Single Source of Truth) untuk menyimpan dan membaca riwayat deteksi
 * beserta file foto hasil scan.
 *
 * Membungkus [SharedPreferences] dengan skema JSON berversi ([SCHEMA_VERSION] = 1):
 * ```json
 * {
 *   "schema_version": 1,
 *   "records": [
 *     {
 *       "label": "Fresh__Leaf",
 *       "confidence": 0.9642,
 *       "timestamp": 1761661200000,
 *       "photo_file": "scan_uuid.jpg"
 *     }
 *   ]
 * }
 * ```
 * Tetap kompatibel mundur (backward-compatible) dengan format array JSON lama (`[...]`)
 * dan tahan terhadap data JSON yang rusak/korup.
 */
class HistoryRepository(
    private val prefs: SharedPreferences,
    private val photoDirectory: File
) {

    companion object {
        const val PREFS_NAME = "chilileaf_history"
        const val KEY_DETECTIONS = "detections"
        const val PHOTO_DIR_NAME = "history_photos"
        const val SCHEMA_VERSION = 1
        const val MAX_RECORDS = 30

        fun create(context: Context): HistoryRepository {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val photoDir = File(appContext.filesDir, PHOTO_DIR_NAME)
            return HistoryRepository(prefs, photoDir)
        }

        /**
         * Serialisasi daftar [HistoryRecord] ke JSON berversi (`schema_version = 1`).
         */
        fun serializeRecords(
            records: List<HistoryRecord>,
            schemaVersion: Int = SCHEMA_VERSION
        ): String {
            return buildString {
                append("{\"schema_version\":")
                append(schemaVersion)
                append(",\"records\":[")
                records.forEachIndexed { index, record ->
                    if (index > 0) append(",")
                    val safeConfidence = if (record.confidence.isFinite()) {
                        record.confidence.coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }
                    val safePhotoName = sanitizeFileName(record.photoFileName)
                    append("{")
                    append("\"label\":\"").append(escapeJson(record.label)).append("\",")
                    append("\"confidence\":").append(
                        String.format(Locale.US, "%.6f", safeConfidence)
                    ).append(",")
                    append("\"timestamp\":").append(record.timestamp.coerceAtLeast(0L)).append(",")
                    append("\"photo_file\":\"").append(escapeJson(safePhotoName)).append("\"")
                    append("}")
                }
                append("]}")
            }
        }

        /**
         * Deserialisasi string JSON (baik skema v1 `{"schema_version":1,"records":[...]}`
         * maupun format legacy `[...]`) menjadi daftar [HistoryRecord].
         * Jika JSON rusak atau memiliki tipe yang tidak valid, mengembalikan daftar kosong
         * atau hanya record yang valid tanpa melempar exception.
         */
        fun deserializeRecords(rawJson: String?): List<HistoryRecord> {
            if (rawJson.isNullOrBlank()) return emptyList()
            val trimmed = rawJson.trim()

            return try {
                val root = SimpleJsonParser(trimmed).parseValue()
                val rawRecords: List<*> = when (root) {
                    is Map<*, *> -> {
                        val version = (root["schema_version"] as? Number)?.toInt() ?: SCHEMA_VERSION
                        if (version < 1 || version > SCHEMA_VERSION) {
                            // Tetap coba baca field records bila tersedia, jika tidak kembalikan kosong
                            (root["records"] as? List<*>) ?: return emptyList()
                        } else {
                            (root["records"] as? List<*>) ?: return emptyList()
                        }
                    }
                    is List<*> -> root
                    else -> return emptyList()
                }

                val result = mutableListOf<HistoryRecord>()
                for (item in rawRecords) {
                    val map = item as? Map<*, *> ?: continue
                    val rawLabel = (map["label"] as? String)?.trim().orEmpty()
                    if (rawLabel.isEmpty()) continue

                    val rawConfidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0
                    val safeConfidence = if (rawConfidence.isFinite()) {
                        rawConfidence.coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                    val rawTimestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
                    val safeTimestamp = rawTimestamp.coerceAtLeast(0L)

                    val rawPhoto = (map["photo_file"] as? String).orEmpty()
                    val safePhoto = sanitizeFileName(rawPhoto)

                    result.add(
                        HistoryRecord(
                            label = rawLabel,
                            confidence = safeConfidence,
                            timestamp = safeTimestamp,
                            photoFileName = safePhoto
                        )
                    )
                }
                result
            } catch (_: Exception) {
                emptyList()
            }
        }

        internal fun sanitizeFileName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return ""
            return File(trimmed).name
        }

        private fun escapeJson(value: String): String {
            return buildString(value.length) {
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        else -> {
                            if (ch.code < 0x20) {
                                append(String.format(Locale.US, "\\u%04x", ch.code))
                            } else {
                                append(ch)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Membaca seluruh riwayat deteksi dari [SharedPreferences].
     */
    @Synchronized
    fun getHistory(): List<HistoryRecord> {
        val raw = try {
            prefs.getString(KEY_DETECTIONS, null)
        } catch (_: Exception) {
            null
        }
        return deserializeRecords(raw)
    }

    /**
     * Menyimpan foto scan ke direktori riwayat dan mencatat hasil deteksi ke [SharedPreferences].
     */
    @Synchronized
    fun saveDetection(
        label: String,
        confidence: Double,
        bitmap: Bitmap,
        timestamp: Long = System.currentTimeMillis()
    ): HistoryRecord {
        val photoFileName = saveScanPhoto(bitmap)
        return recordDetection(
            label = label,
            confidence = confidence,
            photoFileName = photoFileName,
            timestamp = timestamp
        )
    }

    /**
     * Mencatat hasil deteksi dengan nama file foto yang sudah ada, menerapkan batas maksimum
     * [MAX_RECORDS] (30 entri), dan menghapus file foto lama yang tergeser.
     */
    @Synchronized
    fun recordDetection(
        label: String,
        confidence: Double,
        photoFileName: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): HistoryRecord {
        val previous = getHistory()
        val safeConfidence = if (confidence.isFinite()) {
            confidence.coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val newRecord = HistoryRecord(
            label = label.trim().ifEmpty { "Tidak diketahui" },
            confidence = safeConfidence,
            timestamp = timestamp.coerceAtLeast(0L),
            photoFileName = sanitizeFileName(photoFileName)
        )

        val keptPrevious = previous.take(MAX_RECORDS - 1)
        val evicted = previous.drop(MAX_RECORDS - 1)

        for (oldRecord in evicted) {
            if (oldRecord.photoFileName.isNotBlank()) {
                File(photoDirectory, oldRecord.photoFileName).delete()
            }
        }

        val updated = buildList(keptPrevious.size + 1) {
            add(newRecord)
            addAll(keptPrevious)
        }

        val serialized = serializeRecords(updated, SCHEMA_VERSION)
        prefs.edit().putString(KEY_DETECTIONS, serialized).apply()
        return newRecord
    }

    /**
     * Mengembalikan [File] foto scan jika ada dan valid di dalam [photoDirectory].
     */
    fun resolvePhotoFile(photoFileName: String): File? {
        val safeName = sanitizeFileName(photoFileName)
        if (safeName.isEmpty()) return null
        val file = File(photoDirectory, safeName)
        return file.takeIf { it.isFile }
    }

    /**
     * Menghapus seluruh catatan riwayat dari [SharedPreferences] dan menghapus semua file foto scan.
     */
    @Synchronized
    fun clearHistory(): Boolean {
        val cleared = prefs.edit().remove(KEY_DETECTIONS).commit()
        if (cleared && photoDirectory.exists()) {
            photoDirectory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        }
        return cleared
    }

    /**
     * Menyimpan [Bitmap] hasil scan sebagai JPEG terkompresi (sisi terpanjang maks 720px)
     * di dalam [photoDirectory].
     */
    fun saveScanPhoto(bitmap: Bitmap): String {
        if (!photoDirectory.exists()) {
            check(photoDirectory.mkdirs()) {
                "Gagal membuat folder foto riwayat."
            }
        }

        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scale = if (maxSide > 720) 720f / maxSide else 1f

        val smallBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val photoName = "scan_${UUID.randomUUID()}.jpg"
        val file = File(photoDirectory, photoName)

        try {
            val written = FileOutputStream(file).use { stream ->
                smallBitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            }
            if (!written) {
                file.delete()
                error("Gagal menyimpan foto deteksi.")
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            if (smallBitmap !== bitmap) {
                smallBitmap.recycle()
            }
        }

        return photoName
    }
}

/**
 * Parser JSON RFC-8259 ringan untuk membaca objek/array riwayat tanpa bergantung pada stub `org.json`
 * di unit test JVM maupun library eksternal.
 */
internal class SimpleJsonParser(private val input: String) {
    private var pos = 0

    fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= input.length) throw IllegalArgumentException("Unexpected end of JSON")
        val value = when (val ch = input[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$ch' at $pos")
        }
        skipWhitespace()
        return value
    }

    private fun parseObject(): Map<String, Any?> {
        expectChar('{')
        skipWhitespace()
        val map = LinkedHashMap<String, Any?>()
        if (pos < input.length && input[pos] == '}') {
            pos++
            return map
        }
        while (true) {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '"') {
                throw IllegalArgumentException("Expected string key at $pos")
            }
            val key = parseString()
            skipWhitespace()
            expectChar(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (pos >= input.length) throw IllegalArgumentException("Unterminated object")
            when (input[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return map
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expectChar('[')
        skipWhitespace()
        val list = ArrayList<Any?>()
        if (pos < input.length && input[pos] == ']') {
            pos++
            return list
        }
        while (true) {
            val element = parseValue()
            list.add(element)
            skipWhitespace()
            if (pos >= input.length) throw IllegalArgumentException("Unterminated array")
            when (input[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return list
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun parseString(): String {
        expectChar('"')
        val sb = StringBuilder()
        while (pos < input.length) {
            val ch = input[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= input.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > input.length) {
                                throw IllegalArgumentException("Invalid unicode escape")
                            }
                            val hex = input.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw IllegalArgumentException("Invalid escape '\\$esc'")
                    }
                }
                else -> sb.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseNumber(): Number {
        val start = pos
        if (pos < input.length && input[pos] == '-') pos++
        while (pos < input.length && input[pos] in '0'..'9') pos++
        var isFloat = false
        if (pos < input.length && input[pos] == '.') {
            isFloat = true
            pos++
            while (pos < input.length && input[pos] in '0'..'9') pos++
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            isFloat = true
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            while (pos < input.length && input[pos] in '0'..'9') pos++
        }
        val token = input.substring(start, pos)
        return if (isFloat) {
            token.toDouble()
        } else {
            token.toLong()
        }
    }

    private fun parseBoolean(): Boolean {
        return when {
            input.startsWith("true", pos) -> {
                pos += 4
                true
            }
            input.startsWith("false", pos) -> {
                pos += 5
                false
            }
            else -> throw IllegalArgumentException("Invalid boolean token at $pos")
        }
    }

    private fun parseNull(): Any? {
        if (input.startsWith("null", pos)) {
            pos += 4
            return null
        }
        throw IllegalArgumentException("Invalid null token at $pos")
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }

    private fun expectChar(expected: Char) {
        if (pos >= input.length || input[pos] != expected) {
            throw IllegalArgumentException("Expected '$expected' at $pos")
        }
        pos++
    }
}

