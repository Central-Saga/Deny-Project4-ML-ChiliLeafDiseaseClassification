package com.example.chilileafdisease

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakePrefs: InMemorySharedPreferences
    private lateinit var photoDir: File
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        fakePrefs = InMemorySharedPreferences()
        photoDir = tempFolder.newFolder("history_photos")
        repository = HistoryRepository(fakePrefs, photoDir)
    }

    @Test
    fun `serialize and deserialize versioned schema v1 round-trip`() {
        val records = listOf(
            HistoryRecord(
                label = "Fresh__Leaf",
                confidence = 0.9642,
                timestamp = 1761661200000L,
                photoFileName = "scan_abc.jpg"
            ),
            HistoryRecord(
                label = "Chili__leaf_curl",
                confidence = 0.8815,
                timestamp = 1761661100000L,
                photoFileName = "scan_def.jpg"
            )
        )

        val json = HistoryRepository.serializeRecords(records)
        assertTrue(json.contains("\"schema_version\":1"))
        assertTrue(json.contains("\"records\":["))

        val parsed = HistoryRepository.deserializeRecords(json)
        assertEquals(2, parsed.size)
        assertEquals("Fresh__Leaf", parsed[0].label)
        assertEquals(0.9642, parsed[0].confidence, 1e-5)
        assertEquals(1761661200000L, parsed[0].timestamp)
        assertEquals("scan_abc.jpg", parsed[0].photoFileName)
        assertEquals("Chili__leaf_curl", parsed[1].label)
    }

    @Test
    fun `deserialize backward-compatible legacy unversioned JSONArray`() {
        val legacyJson = """
            [
              {
                "label": "Chili__Cercospora_leaf_spot",
                "confidence": 0.9125,
                "timestamp": 1760000000000,
                "photo_file": "scan_legacy.jpg"
              },
              {
                "label": "Chili__Anthracnose",
                "confidence": 0.845
              }
            ]
        """.trimIndent()

        fakePrefs.edit().putString(HistoryRepository.KEY_DETECTIONS, legacyJson).commit()
        val history = repository.getHistory()

        assertEquals(2, history.size)
        assertEquals("Chili__Cercospora_leaf_spot", history[0].label)
        assertEquals(0.9125, history[0].confidence, 1e-5)
        assertEquals(1760000000000L, history[0].timestamp)
        assertEquals("scan_legacy.jpg", history[0].photoFileName)
        assertEquals("Chili__Anthracnose", history[1].label)
        assertEquals(0L, history[1].timestamp)
        assertEquals("", history[1].photoFileName)
    }

    @Test
    fun `corrupted JSON and malformed records are handled safely without crashing`() {
        // 1. JSON terpotong / syntax rusak
        fakePrefs.edit().putString(HistoryRepository.KEY_DETECTIONS, "{\"schema_version\":1,\"records\":[{\"label\":").commit()
        assertTrue(repository.getHistory().isEmpty())

        // 2. Root bukan objek atau array
        fakePrefs.edit().putString(HistoryRepository.KEY_DETECTIONS, "\"bukan_json_objek\"").commit()
        assertTrue(repository.getHistory().isEmpty())

        // 3. Campuran record rusak (label kosong, bukan objek, confidence di luar [0,1], path traversal)
        val mixedCorruptJson = """
            {
              "schema_version": 1,
              "records": [
                "elemen_liar",
                {"label": "   ", "confidence": 0.5},
                {
                  "label": "Fresh__Leaf",
                  "confidence": 1.75,
                  "timestamp": -999,
                  "photo_file": "../../etc/passwd"
                },
                {
                  "label": "Chili__leaf_curl",
                  "confidence": -0.4,
                  "timestamp": 1761661500000,
                  "photo_file": "scan_valid.jpg"
                }
              ]
            }
        """.trimIndent()
        fakePrefs.edit().putString(HistoryRepository.KEY_DETECTIONS, mixedCorruptJson).commit()

        val recovered = repository.getHistory()
        assertEquals(2, recovered.size)
        assertEquals("Fresh__Leaf", recovered[0].label)
        assertEquals(1.0, recovered[0].confidence, 1e-6)
        assertEquals(0L, recovered[0].timestamp)
        assertEquals("passwd", recovered[0].photoFileName)

        assertEquals("Chili__leaf_curl", recovered[1].label)
        assertEquals(0.0, recovered[1].confidence, 1e-6)
        assertEquals(1761661500000L, recovered[1].timestamp)
        assertEquals("scan_valid.jpg", recovered[1].photoFileName)
    }

    @Test
    fun `recordDetection enforces max 30 records and deletes evicted photo files`() {
        for (i in 1..32) {
            val photoName = "scan_$i.jpg"
            File(photoDir, photoName).writeText("dummy_jpeg_$i")
            repository.recordDetection(
                label = "Fresh__Leaf_$i",
                confidence = 0.90,
                photoFileName = photoName,
                timestamp = i * 1000L
            )
        }

        val history = repository.getHistory()
        assertEquals(HistoryRepository.MAX_RECORDS, history.size)
        assertEquals("Fresh__Leaf_32", history.first().label)
        assertEquals("Fresh__Leaf_3", history.last().label)

        // Foto 1 dan 2 harus sudah terhapus karena melewati batas 30 riwayat
        assertFalse(File(photoDir, "scan_1.jpg").exists())
        assertFalse(File(photoDir, "scan_2.jpg").exists())
        assertTrue(File(photoDir, "scan_3.jpg").exists())
        assertTrue(File(photoDir, "scan_32.jpg").exists())
        assertNotNull(repository.resolvePhotoFile("scan_32.jpg"))
        assertNull(repository.resolvePhotoFile("scan_1.jpg"))
    }

    @Test
    fun `clearHistory removes stored records and deletes all photo files`() {
        val photoFile = File(photoDir, "scan_to_clear.jpg").apply { writeText("img") }
        repository.recordDetection("Chili__Anthracnose", 0.93, photoFile.name, 1000L)
        assertEquals(1, repository.getHistory().size)
        assertTrue(photoFile.exists())

        val cleared = repository.clearHistory()
        assertTrue(cleared)
        assertTrue(repository.getHistory().isEmpty())
        assertFalse(photoFile.exists())
    }
}

internal class InMemorySharedPreferences : SharedPreferences {
    private val data = LinkedHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String?, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = values
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removals.add(key)
        }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
        override fun commit(): Boolean {
            if (clearAll) data.clear()
            for (k in removals) data.remove(k)
            for ((k, v) in pending) {
                if (v == null) data.remove(k) else data[k] = v
            }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}

