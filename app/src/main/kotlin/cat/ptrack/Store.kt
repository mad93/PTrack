package cat.ptrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONException
import org.json.JSONObject

class Store(private val context: Context, name: String = "ptrack.db") : SQLiteOpenHelper(context, name, null, 6) {
    internal val vehicles = VehicleProfiles(this)
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)")
        db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)")
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)")
        db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)")
        ConsumptionRepository.createSchema(db); summarySchema(db); backupSchema(db)
        dcChargeSchema(db)
        vehicleSchema(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) ConsumptionRepository.createSchema(db)
        if (oldVersion < 3) summarySchema(db)
        if (oldVersion < 4) backupSchema(db)
        if (oldVersion < 5) dcChargeSchema(db)
        if (oldVersion < 6) vehicleSchema(db)
    }
    private fun vehicleSchema(db: SQLiteDatabase) {
        val initialId = VehicleProfiles.createSchema(db, context.getSharedPreferences("settings", 0).getString("address", null))
        for (table in listOf("trips", "dc_charge_samples")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN vehicle_id TEXT REFERENCES vehicles(id)")
            db.execSQL("UPDATE $table SET vehicle_id=?", arrayOf(initialId))
            db.execSQL("""CREATE TRIGGER ${table}_require_vehicle BEFORE INSERT ON $table
                WHEN NEW.vehicle_id IS NULL OR NOT EXISTS(SELECT 1 FROM vehicles WHERE id=NEW.vehicle_id)
                BEGIN SELECT RAISE(ABORT,'Missing vehicle profile'); END""")
        }
        db.execSQL("CREATE INDEX trip_vehicle_start ON trips(vehicle_id,start)")
        db.execSQL("CREATE INDEX dc_vehicle_session ON dc_charge_samples(vehicle_id,session,time)")
        db.execSQL("""UPDATE vehicles SET (soh,soh_at) = (SELECT value,time FROM readings
            JOIN trips ON trips.id=readings.trip WHERE signal='soh' AND value BETWEEN 0 AND 100 AND demo=0
            ORDER BY time DESC,readings.id DESC LIMIT 1) WHERE id=? AND EXISTS(
                SELECT 1 FROM readings JOIN trips ON trips.id=readings.trip WHERE signal='soh' AND value BETWEEN 0 AND 100 AND demo=0)""",
            arrayOf(initialId))
        ConsumptionRepository.addVehicleScope(db)
    }
    fun begin(demo: Boolean, vehicleId: String = vehicles.active().id): Long = writableDatabase.insertOrThrow("trips", null, ContentValues().apply {
        put("start", System.currentTimeMillis()); put("demo", demo); put("status", "recording"); put("vehicle_id", vehicleId)
    })
    private fun summarySchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trip_summaries(trip INTEGER PRIMARY KEY, version INTEGER NOT NULL, stats TEXT NOT NULL)")
    }
    private fun backupSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trip_backup_keys(trip INTEGER PRIMARY KEY, backup_key TEXT NOT NULL UNIQUE)")
    }
    private fun dcChargeSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE dc_charge_samples(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, session INTEGER NOT NULL, soc REAL NOT NULL, kw REAL NOT NULL, pack REAL NOT NULL, ambient REAL NOT NULL)")
        db.execSQL("CREATE INDEX dc_charge_session ON dc_charge_samples(session)")
    }
    internal fun addDcCharge(sample: DcChargeProfile.Observation, vehicleId: String = vehicles.active().id) {
        writableDatabase.insertOrThrow("dc_charge_samples", null, ContentValues().apply {
            put("time", sample.time); put("session", sample.session); put("soc", sample.soc)
            put("kw", sample.kw); put("pack", sample.packC); put("ambient", sample.ambientC)
            put("vehicle_id", vehicleId)
        })
    }
    internal fun dcChargeProfile(vehicleId: String = vehicles.active().id): DcChargeProfile = readableDatabase.rawQuery(
        "SELECT session,time,soc,kw,pack,ambient FROM dc_charge_samples WHERE vehicle_id=? AND session IN " +
            "(SELECT DISTINCT session FROM dc_charge_samples WHERE vehicle_id=? ORDER BY session DESC LIMIT 50) ORDER BY time",
        arrayOf(vehicleId, vehicleId)
    ).use { cursor ->
        DcChargeProfile(buildList {
            while (cursor.moveToNext()) add(DcChargeProfile.Observation(cursor.getLong(0), cursor.getLong(1),
                cursor.getDouble(2), cursor.getDouble(3), cursor.getDouble(4), cursor.getDouble(5)))
        })
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
        addEnergyReadings(id, stats)
        return stats.summary()
    }

    internal fun addEnergyReadings(id: Long, stats: TripStats) {
        readableDatabase.rawQuery("SELECT time,value FROM readings WHERE trip=? AND signal='energy' ORDER BY time,id", arrayOf(id.toString())).use { c ->
            while (c.moveToNext()) stats.energy(c.getLong(0), required(c, 1))
        }
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
    fun tripsBetween(from: Long, until: Long, vehicleId: String? = null): Cursor = readableDatabase.rawQuery(
        """SELECT trips.id,start,end,demo,status,name FROM trips JOIN vehicles ON vehicles.id=trips.vehicle_id
            WHERE start>=? AND start<? ${if (vehicleId == null) "" else "AND vehicle_id=?"} ORDER BY start DESC,trips.id DESC""",
        listOfNotNull(from.toString(), until.toString(), vehicleId).toTypedArray()
    )
    internal fun tripVehicle(id: Long): String = readableDatabase.rawQuery(
        "SELECT name FROM trips JOIN vehicles ON vehicles.id=trips.vehicle_id WHERE trips.id=?", arrayOf(id.toString())
    ).use { check(it.moveToFirst()); it.getString(0) }
    fun isDemo(id: Long): Boolean = readableDatabase.rawQuery("SELECT demo FROM trips WHERE id=?", arrayOf(id.toString())).use { it.moveToFirst() && it.getInt(0) == 1 }
}
