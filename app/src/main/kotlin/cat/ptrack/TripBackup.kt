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

/** The column order in these lists is part of the backup format. */
internal object TripBackup {
    private const val FORMAT = "PTrack trip backup"
    private const val VERSION = 2
    private val keyPattern = Regex("[0-9a-f]{32}")
    private val vehicleKeyPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    private val tripColumns = listOf("start", "end", "demo", "status")
    private val vehicleColumns = listOf("id", "name", "model", "capacity_kwh", "obd_address", "archived", "updated_at", "soh", "soh_at")
    private val dcColumns = listOf("vehicle_id", "time", "session", "soc", "kw", "pack", "ambient")
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
            val revision = "v$VERSION:" + listOf(
                VehicleProfiles.initialId(db),
                revision(db, "COUNT(*),COALESCE(MAX(id),0)", "trips WHERE status!='recording'"),
                revision(db, "COUNT(*),SUM(updated_at),SUM(soh_at),SUM(archived)", "vehicles"),
                revision(db, "COUNT(*),COALESCE(MAX(id),0)", "dc_charge_samples")
            ).joinToString(":")
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

    private fun revision(db: SQLiteDatabase, columns: String, table: String) =
        db.rawQuery("SELECT $columns FROM $table", null).use { row ->
            check(row.moveToFirst())
            (0 until row.columnCount).joinToString(":") { row.getLong(it).toString() }
        }

    private fun writeTrips(db: SQLiteDatabase, writer: JsonWriter) {
        writer.beginObject().name("format").value(FORMAT).name("version").value(VERSION.toLong())
        writer.name("initial_vehicle_id").value(VehicleProfiles.initialId(db))
        writer.name("vehicles").beginArray()
        db.query("vehicles", vehicleColumns.toTypedArray(), null, null, null, null, "id").use { rows ->
            while (rows.moveToNext()) writeValues(writer, rows)
        }
        writer.endArray().name("trips").beginArray()
        db.rawQuery("""
            SELECT trips.id, backup_key, start, end, demo, status, vehicle_id
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
        writer.endArray().name("dc_charge_samples").beginArray()
        db.query("dc_charge_samples", dcColumns.toTypedArray(), null, null, null, null, "id").use { rows ->
            while (rows.moveToNext()) writeValues(writer, rows)
        }
        writer.endArray().endObject()
    }

    /** Imports all new trips in one transaction. Re-importing a trip preserves the local copy. */
    fun read(store: Store, file: File): Int {
        val db = store.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            checkRecording(automatic = false)
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
        require(reader.nextName() == "version")
        val version = reader.nextInt()
        require(version in 1..VERSION)
        val initial = VehicleProfiles.initialId(db)
        val vehicles = if (version >= 2) {
            require(reader.nextName() == "initial_vehicle_id")
            val sourceInitial = reader.nextString()
            val placeholder = VehicleProfiles.unusedInitialId(db)
            readVehicles(db, reader).also { ids ->
                require(sourceInitial in ids)
                if (placeholder != null && placeholder !in ids) VehicleProfiles.adoptInitial(db, placeholder, sourceInitial)
            }
        } else setOf(initial)
        val columns = if (version >= 2) tripColumns + "vehicle_id" else tripColumns
        require(reader.nextName() == "trips")
        reader.beginArray()
        var count = 0
        val keys = HashSet<String>()
        while (reader.hasNext()) {
            reader.beginArray()
            require(reader.peek() == JsonToken.STRING)
            val key = reader.nextString()
            require(keyPattern.matches(key) && keys.add(key))
            val trip = readValues(reader, columns)
            if (version >= 2) require(trip.getAsString("vehicle_id") in vehicles)
            else trip.put("vehicle_id", initial)
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
        if (version >= 2) {
            readDcCharge(db, reader, vehicles)
            VehicleProfiles.repairSelection(db)
        }
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT)
        return count
    }

    private fun readVehicles(db: SQLiteDatabase, reader: JsonReader): Set<String> {
        require(reader.nextName() == "vehicles")
        val ids = HashSet<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            val row = readValues(reader, vehicleColumns)
            val id = row.getAsString("id")
            val name = row.getAsString("name")
            val model = row.getAsString("model")
            val capacity = row.getAsDouble("capacity_kwh")
            val address = row.getAsString("obd_address")
            val archived = row.getAsLong("archived")
            val updatedAt = row.getAsLong("updated_at")
            val soh = row.getAsDouble("soh")
            val sohAt = row.getAsLong("soh_at")
            require(vehicleKeyPattern.matches(id) && ids.add(id))
            require(name.isNotBlank() && name.length <= 80 && (model == null || model.length <= 80))
            require((capacity == null || capacity > 0) && (address == null || VehicleProfiles.validAddress(address)))
            require(archived in 0L..1L && updatedAt >= 0 && sohAt >= 0 && (soh == null || soh in 0.0..100.0))
            VehicleProfiles.merge(db, VehicleProfile(id, name, model, capacity, address, archived == 1L, updatedAt, soh, sohAt))
        }
        reader.endArray()
        return ids
    }

    private fun readDcCharge(db: SQLiteDatabase, reader: JsonReader, vehicles: Set<String>) {
        require(reader.nextName() == "dc_charge_samples")
        reader.beginArray()
        while (reader.hasNext()) {
            val row = readValues(reader, dcColumns)
            val vehicleId = row.getAsString("vehicle_id")
            val time = row.getAsLong("time")
            val session = row.getAsLong("session")
            require(vehicleId in vehicles && row.getAsDouble("soc") in 0.0..100.0 && row.getAsDouble("kw") > 0)
            val exists = db.rawQuery("SELECT 1 FROM dc_charge_samples WHERE vehicle_id=? AND session=? AND time=?",
                arrayOf(vehicleId, session.toString(), time.toString())).use { it.moveToFirst() }
            if (!exists) db.insertOrThrow("dc_charge_samples", null, row)
        }
        reader.endArray()
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
                "start", "end", "demo", "time", "version", "archived", "updated_at", "soh_at", "session" -> {
                    require(token == JsonToken.NUMBER)
                    values.put(column, reader.nextString().toLong())
                }
                "status", "signal", "raw", "stats", "id", "name", "model", "obd_address", "vehicle_id" -> {
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

    private val nullableColumns = setOf("end", "soc", "voltage", "current", "speed", "lat", "lon", "altitude", "raw", "signal", "value",
        "model", "capacity_kwh", "obd_address", "soh")

    private fun checkRecording(automatic: Boolean) {
        val recording = if (automatic) RecordingService.activeTrip >= 0 else RecordingService.running
        if (recording) throw RecordingInProgress()
    }

    class RecordingInProgress : IllegalStateException()
}
