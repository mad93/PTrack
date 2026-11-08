package cat.ptrack

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import cat.ptrack.ConsumptionModel.*
import kotlin.math.floor

/** Call from a worker: queries rebuild a consistent historical snapshot. */
class ConsumptionRepository(
    private val store: Store,
    private val binWidth: Int = 10,
    private val vehicleId: String = store.vehicles.active().id
) : ConsumptionModel {
    init { ConsumptionLearner(binWidth) }
    fun recalculate() {
        val db = store.writableDatabase
        db.beginTransaction()
        try { rebuild(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    private fun learn(db: SQLiteDatabase, capacityEstimates: MutableList<Double>? = null): ConsumptionLearner {
        val learner = ConsumptionLearner(binWidth)
        db.rawQuery("SELECT id FROM trips WHERE vehicle_id=? AND demo=0 AND status='complete' ORDER BY start DESC,id DESC",
            arrayOf(vehicleId)).use { trips ->
            while (trips.moveToNext()) {
                val samples = mutableListOf<Sample>()
                db.rawQuery("SELECT time,soc,voltage,current,speed,lat,lon,altitude FROM samples WHERE trip=? ORDER BY time,id", arrayOf(trips.getString(0))).use { c ->
                    while (c.moveToNext()) samples.add(Sample(c.getLong(0), required(c, 1), required(c, 2), required(c, 3), required(c, 4),
                        optional(c, 5), optional(c, 6), optional(c, 7), null))
                }
                learner.addTrip(samples)
                if (capacityEstimates != null && capacityEstimates.size < PlanningEnergy.MAX_CAPACITY_TRIPS) {
                    val stats = TripStats()
                    samples.forEach(stats::add)
                    if (stats.startSoc - stats.endSoc < 10 || stats.count < 30) continue
                    store.addEnergyReadings(trips.getLong(0), stats)
                    val stationaryCharging = samples.any { it.valid() && it.speed <= 1 && it.power() < -3 }
                    PlanningEnergy.capacityFrom(stats.summary(), stationaryCharging)?.let(capacityEstimates::add)
                }
            }
        }
        return learner
    }

    /** An in-memory profile for a whole planning request, with no writes to the trip database. */
    internal fun planningProfile(referenceCapacityKwh: Double? = store.vehicles.find(vehicleId)?.capacityKwh): PlanningEnergy {
        val capacities = mutableListOf<Double>()
        val estimates = learn(store.readableDatabase, capacities).buckets().mapValues { it.value.estimate() }
        val learned = PlanningEnergy.learnedCapacity(capacities)
        if (learned == null && referenceCapacityKwh == null) throw MissingVehicleCapacity()
        return PlanningEnergy(learned, estimates, binWidth, referenceCapacityKwh ?: checkNotNull(learned))
    }

    private fun rebuild(db: SQLiteDatabase) {
        val learner = learn(db)
        db.delete("consumption_profiles", "vehicle_id=?", arrayOf(vehicleId))
        learner.buckets().forEach { (key, b) ->
            val row = ContentValues().apply {
                put("vehicle_id", vehicleId); put("speed_bin", key.speedBin); put("terrain", key.terrain.name); put("km", b.km)
                put("consumed", b.consumedKwh); put("recovered", b.recoveredKwh); put("intervals", b.intervals); put("trips", b.trips)
            }
            db.insertOrThrow("consumption_profiles", null, row)
        }
        db.execSQL("INSERT OR REPLACE INTO consumption_model(vehicle_id,version,bin_width,rebuilt_at) VALUES(?,1,?,?)",
            arrayOf<Any>(vehicleId, binWidth, System.currentTimeMillis()))
    }
    override fun estimate(speedKmh: Double, terrain: Terrain?): Estimate {
        require(speedKmh.isFinite() && speedKmh in 5.0..250.0 && terrain != null) { "speed/terrain" }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            rebuild(db)
            val bucket = ConsumptionLearner.Bucket()
            db.rawQuery("SELECT km,consumed,recovered,intervals,trips FROM consumption_profiles WHERE vehicle_id=? AND speed_bin=? AND terrain=?",
                arrayOf(vehicleId, floor(speedKmh / binWidth).toInt().toString(), terrain.name)).use { c ->
                if (c.moveToFirst()) { bucket.km = c.getDouble(0); bucket.consumedKwh = c.getDouble(1); bucket.recoveredKwh = c.getDouble(2); bucket.intervals = c.getLong(3); bucket.trips = c.getInt(4) }
            }
            val result = bucket.estimate(); db.setTransactionSuccessful(); return result
        } finally { db.endTransaction() }
    }
    private fun required(c: Cursor, index: Int) = optional(c, index) ?: Double.NaN
    private fun optional(c: Cursor, index: Int): Double? = if (c.isNull(index)) null else c.getDouble(index)
    internal class MissingVehicleCapacity : IllegalStateException()

    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE consumption_profiles(speed_bin INTEGER NOT NULL, terrain TEXT NOT NULL, km REAL NOT NULL, consumed REAL NOT NULL, recovered REAL NOT NULL, intervals INTEGER NOT NULL, trips INTEGER NOT NULL, PRIMARY KEY(speed_bin,terrain))")
            db.execSQL("CREATE TABLE consumption_model(id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL, bin_width INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)")
        }

        internal fun addVehicleScope(db: SQLiteDatabase) {
            // Derived data is rebuilt from the original trips on demand.
            db.execSQL("DROP TABLE consumption_profiles")
            db.execSQL("DROP TABLE consumption_model")
            db.execSQL("""CREATE TABLE consumption_profiles(vehicle_id TEXT NOT NULL REFERENCES vehicles(id),
                speed_bin INTEGER NOT NULL, terrain TEXT NOT NULL, km REAL NOT NULL, consumed REAL NOT NULL,
                recovered REAL NOT NULL, intervals INTEGER NOT NULL, trips INTEGER NOT NULL,
                PRIMARY KEY(vehicle_id,speed_bin,terrain))""")
            db.execSQL("""CREATE TABLE consumption_model(vehicle_id TEXT PRIMARY KEY NOT NULL REFERENCES vehicles(id),
                version INTEGER NOT NULL, bin_width INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)""")
        }
    }
}
