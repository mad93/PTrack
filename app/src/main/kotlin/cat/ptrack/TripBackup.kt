package cat.ptrack

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The column order in these lists is part of the version 1 backup format. */
internal object TripBackup {
    private const val FORMAT = "PTrack trip backup"
    private const val VERSION = 1
    private val keyPattern = Regex("[0-9a-f]{32}")
    private val tripColumns = listOf("start", "end", "demo", "status")
    private val tables = linkedMapOf(
        "samples" to listOf("time", "soc", "voltage", "current", "speed", "lat", "lon", "altitude", "raw"),
        "readings" to listOf("time", "signal", "value", "raw"),
        "trip_summaries" to listOf("version", "stats")
    )

    fun write(store: Store, file: File, automatic: Boolean = false, previous: String? = null): String? {
        val db = store.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            checkRecording(automatic)
            if (!automatic) store.recover()
            val revision = db.rawQuery("SELECT COUNT(*), COALESCE(MAX(id),0) FROM trips WHERE status!='recording'", null).use {
                it.moveToFirst()
                "${it.getLong(0)}:${it.getLong(1)}"
            }
            if (automatic && revision == previous) return null
            db.execSQL("""
                INSERT INTO trip_backup_keys(trip, backup_key)
                SELECT id, lower(hex(randomblob(16))) FROM trips
                WHERE status!='recording' AND id NOT IN (SELECT trip FROM trip_backup_keys)
            """.trimIndent())
            file.outputStream().buffered().use { output ->
                JsonWriter(GZIPOutputStream(output).bufferedWriter()).use { writer -> writeTrips(db, writer) }
            }
            db.setTransactionSuccessful()
            return revision
        } finally {
            db.endTransaction()
        }
    }

    private fun writeTrips(db: SQLiteDatabase, writer: JsonWriter) {
        writer.beginObject().name("format").value(FORMAT).name("version").value(VERSION.toLong())
            .name("trips").beginArray()
        db.rawQuery("""
            SELECT trips.id, backup_key, start, end, demo, status
            FROM trips JOIN trip_backup_keys ON trip = trips.id
            WHERE status!='recording' ORDER BY trips.id
        """.trimIndent(), null).use { trips ->
            while (trips.moveToNext()) {
                val id = trips.getString(0)
                writer.beginArray().value(trips.getString(1))
                writeValues(writer, trips, 2)
                for ((table, columns) in tables) {
                    writer.beginArray()
                    val order = if (table == "trip_summaries") "trip" else "id"
                    db.query(table, columns.toTypedArray(), "trip=?", arrayOf(id), null, null, order).use { rows ->
                        while (rows.moveToNext()) writeValues(writer, rows)
                    }
                    writer.endArray()
                }
                writer.endArray()
            }
        }
        writer.endArray().endObject()
    }

    /** Imports all new trips in one transaction. Re-importing a trip preserves the local copy. */
    fun read(store: Store, file: File, automatic: Boolean = false): Int {
        val db = store.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            checkRecording(automatic)
            val added = file.inputStream().buffered().use { input ->
                JsonReader(GZIPInputStream(input).bufferedReader()).use { reader -> readTrips(db, reader) }
            }
            db.setTransactionSuccessful()
            return added
        } finally {
            db.endTransaction()
        }
    }

    private fun readTrips(db: SQLiteDatabase, reader: JsonReader): Int {
        reader.beginObject()
        require(reader.nextName() == "format" && reader.nextString() == FORMAT)
        require(reader.nextName() == "version" && reader.nextInt() == VERSION)
        require(reader.nextName() == "trips")
        reader.beginArray()
        var count = 0
        val keys = HashSet<String>()
        while (reader.hasNext()) {
            reader.beginArray()
            require(reader.peek() == JsonToken.STRING)
            val key = reader.nextString()
            require(keyPattern.matches(key) && keys.add(key))
            val trip = readValues(reader, tripColumns)
            val demo = trip.getAsLong("demo")
            val status = trip.getAsString("status")
            require((demo == 0L || demo == 1L) && (status == "complete" || status == "interrupted"))
            val exists = db.rawQuery("SELECT 1 FROM trip_backup_keys WHERE backup_key=?", arrayOf(key)).use { it.moveToFirst() }
            val id = if (exists) null else db.insertOrThrow("trips", null, trip).also {
                db.execSQL("INSERT INTO trip_backup_keys(trip, backup_key) VALUES(?,?)", arrayOf<Any>(it, key))
                count++
            }
            for ((table, columns) in tables) {
                reader.beginArray()
                var rows = 0
                while (reader.hasNext()) {
                    if (table == "trip_summaries") require(rows == 0)
                    val values = readValues(reader, columns)
                    if (id != null) {
                        values.put("trip", id)
                        db.insertOrThrow(table, null, values)
                    }
                    rows++
                }
                reader.endArray()
            }
            reader.endArray()
        }
        reader.endArray()
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT)
        return count
    }

    private fun writeValues(writer: JsonWriter, row: Cursor, start: Int = 0) {
        writer.beginArray()
        for (index in start until row.columnCount) {
            when (row.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                Cursor.FIELD_TYPE_INTEGER -> writer.value(row.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> writer.value(row.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> writer.value(row.getString(index))
                else -> throw IOException("Unsupported backup value")
            }
        }
        writer.endArray()
    }

    private fun readValues(reader: JsonReader, columns: List<String>): ContentValues {
        val values = ContentValues(columns.size)
        reader.beginArray()
        for (column in columns) {
            val token = reader.peek()
            if (token == JsonToken.NULL) {
                require(column in nullableColumns)
                reader.nextNull()
                values.putNull(column)
            } else when (column) {
                "start", "end", "demo", "time", "version" -> {
                    require(token == JsonToken.NUMBER)
                    values.put(column, reader.nextString().toLong())
                }
                "status", "signal", "raw", "stats" -> {
                    require(token == JsonToken.STRING)
                    values.put(column, reader.nextString())
                }
                else -> {
                    require(token == JsonToken.NUMBER)
                    values.put(column, reader.nextDouble().also { require(it.isFinite()) })
                }
            }
        }
        reader.endArray()
        return values
    }

    private val nullableColumns = setOf("end", "soc", "voltage", "current", "speed", "lat", "lon", "altitude", "raw", "signal", "value")

    private fun checkRecording(automatic: Boolean) {
        val recording = if (automatic) RecordingService.activeTrip >= 0 else RecordingService.running
        if (recording) throw RecordingInProgress()
    }

    class RecordingInProgress : IllegalStateException()
}
