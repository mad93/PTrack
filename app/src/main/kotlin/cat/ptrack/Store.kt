package cat.ptrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONException
import org.json.JSONObject

class Store(context: Context, name: String = "ptrack.db") : SQLiteOpenHelper(context, name, null, 4) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)")
        db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)")
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)")
        db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)")
        ConsumptionRepository.createSchema(db); summarySchema(db); backupSchema(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) ConsumptionRepository.createSchema(db)
        if (oldVersion < 3) summarySchema(db)
        if (oldVersion < 4) backupSchema(db)
    }
    fun begin(demo: Boolean): Long = writableDatabase.insertOrThrow("trips", null, ContentValues().apply {
        put("start", System.currentTimeMillis()); put("demo", demo); put("status", "recording")
    })
    private fun summarySchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trip_summaries(trip INTEGER PRIMARY KEY, version INTEGER NOT NULL, stats TEXT NOT NULL)")
    }
    private fun backupSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trip_backup_keys(trip INTEGER PRIMARY KEY, backup_key TEXT NOT NULL UNIQUE)")
    }
    fun recover() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = mutableListOf<Long>()
            db.rawQuery("SELECT id FROM trips WHERE status='recording'", null).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
            db.execSQL("UPDATE trips SET status='interrupted', end=COALESCE((SELECT MAX(time) FROM samples WHERE trip=trips.id),start) WHERE status='recording'")
            for (id in ids) saveSummary(id, buildSummary(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun finish(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE trips SET end=COALESCE((SELECT MAX(time) FROM samples WHERE trip=?),start),status='complete' WHERE id=?", arrayOf(id, id))
            saveSummary(id, buildSummary(id)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun add(trip: Long, s: Sample) {
        val row = ContentValues().apply {
            put("trip", trip); put("time", s.time); put("soc", s.soc); put("voltage", s.voltage)
            put("current", s.current); put("speed", s.speed); put("lat", s.latitude)
            put("lon", s.longitude); put("altitude", s.altitude); put("raw", s.raw)
        }
        writableDatabase.insertOrThrow("samples", null, row)
    }
    fun reading(trip: Long, signal: String, value: Double?, raw: String?) {
        val row = ContentValues().apply {
            put("trip", trip); put("time", System.currentTimeMillis()); put("signal", signal)
            put("value", value?.takeIf { it.isFinite() }); put("raw", raw)
        }
        writableDatabase.insertOrThrow("readings", null, row)
    }
    fun samples(trip: Long): List<Sample> {
        val result = mutableListOf<Sample>()
        readableDatabase.rawQuery("SELECT time,soc,voltage,current,speed,lat,lon,altitude,raw FROM samples WHERE trip=? ORDER BY time,id", arrayOf(trip.toString())).use { c ->
            while (c.moveToNext()) result.add(Sample(c.getLong(0), required(c, 1), required(c, 2), required(c, 3), required(c, 4),
                number(c, 5), number(c, 6), number(c, 7), c.getString(8)))
        }
        return result
    }
    private fun number(c: Cursor, n: Int): Double? = if (c.isNull(n)) null else c.getDouble(n)
    private fun required(c: Cursor, n: Int) = number(c, n) ?: Double.NaN
    /** Call on a worker. Legacy summaries are derived only from saved readings. */
    fun summary(id: Long): TripSummary {
        val closed = readableDatabase.rawQuery("SELECT status FROM trips WHERE id=?", arrayOf(id.toString())).use { c ->
            require(c.moveToFirst()) { "Unknown trip" }; c.getString(0) != "recording"
        }
        if (closed) readableDatabase.rawQuery("SELECT version,stats FROM trip_summaries WHERE trip=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst() && c.getInt(0) == 2) try {
                val json = JSONObject(c.getString(1))
                return TripSummary.fromValues(DoubleArray(TripSummary.KEYS.size) { json.optDouble(TripSummary.KEYS[it], Double.NaN) })
            } catch (_: JSONException) { }
        }
        val result = buildSummary(id)
        if (closed) saveSummary(id, result)
        return result
    }
    private fun buildSummary(id: Long): TripSummary {
        val stats = TripStats(); samples(id).forEach(stats::add)
        readableDatabase.rawQuery("SELECT time,value FROM readings WHERE trip=? AND signal='energy' ORDER BY time,id", arrayOf(id.toString())).use { c ->
            while (c.moveToNext()) stats.energy(c.getLong(0), required(c, 1))
        }
        return stats.summary()
    }
    private fun saveSummary(id: Long, summary: TripSummary) {
        try {
            val json = JSONObject(); val values = summary.values()
            for (i in values.indices) json.put(TripSummary.KEYS[i], if (values[i].isFinite()) values[i] else JSONObject.NULL)
            val row = ContentValues().apply { put("trip", id); put("version", 2); put("stats", json.toString()) }
            check(writableDatabase.insertWithOnConflict("trip_summaries", null, row, SQLiteDatabase.CONFLICT_REPLACE) >= 0) { "Summary persistence failed" }
        } catch (e: JSONException) { throw IllegalStateException("Summary serialization", e) }
    }
    fun trips(): Cursor = readableDatabase.rawQuery("SELECT id,start,end,demo,status FROM trips ORDER BY start DESC,id DESC", null)
    fun tripsBetween(from: Long, until: Long): Cursor = readableDatabase.rawQuery(
        "SELECT id,start,end,demo,status FROM trips WHERE start>=? AND start<? ORDER BY start DESC,id DESC",
        arrayOf(from.toString(), until.toString())
    )
    fun isDemo(id: Long): Boolean = readableDatabase.rawQuery("SELECT demo FROM trips WHERE id=?", arrayOf(id.toString())).use { it.moveToFirst() && it.getInt(0) == 1 }
}
