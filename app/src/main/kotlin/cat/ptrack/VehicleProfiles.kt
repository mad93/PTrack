package cat.ptrack

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.util.Locale
import java.util.UUID

internal data class VehicleProfile(
    val id: String,
    val name: String,
    val model: String?,
    val capacityKwh: Double?,
    val obdAddress: String?,
    val archived: Boolean = false,
    val updatedAt: Long = 0,
    val soh: Double? = null,
    val sohAt: Long = 0
) {
    val label get() = listOfNotNull(name, model?.takeUnless { it.equals(name, ignoreCase = true) }).joinToString(" · ")
}

internal class VehicleProfiles(private val store: Store) {
    fun active(): VehicleProfile = store.readableDatabase.rawQuery(
        "SELECT $COLUMNS FROM vehicles WHERE id=(SELECT vehicle_id FROM vehicle_selection WHERE id=1)", null
    ).use { check(it.moveToFirst()); fromRow(it) }

    fun list(): List<VehicleProfile> = all(store.readableDatabase)

    fun find(id: String): VehicleProfile? = store.readableDatabase.rawQuery(
        "SELECT $COLUMNS FROM vehicles WHERE id=?", arrayOf(id)
    ).use { if (it.moveToFirst()) fromRow(it) else null }

    fun matches(address: String): List<VehicleProfile> = store.readableDatabase.rawQuery(
        "SELECT $COLUMNS FROM vehicles WHERE archived=0 AND obd_address=? ORDER BY name COLLATE NOCASE,id",
        arrayOf(normalize(address))
    ).use { rows -> buildList { while (rows.moveToNext()) add(fromRow(rows)) } }

    fun addresses(): Set<String> = list().filterNot { it.archived }.mapNotNull { it.obdAddress }.toSet()

    fun select(id: String): Boolean = store.writableDatabase.compileStatement(
        "UPDATE vehicle_selection SET vehicle_id=? WHERE id=1 AND EXISTS(SELECT 1 FROM vehicles WHERE id=? AND archived=0)"
    ).use {
        it.bindString(1, id); it.bindString(2, id)
        it.executeUpdateDelete() == 1
    }

    fun save(vehicle: VehicleProfile) {
        val address = vehicle.obdAddress?.let(::normalize)
        require(address == null || validAddress(address)) { "Invalid OBD address" }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val before = db.rawQuery("SELECT updated_at FROM vehicles WHERE id=?", arrayOf(vehicle.id)).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            if (vehicle.archived) check(active().id != vehicle.id) { "Active vehicle cannot be archived" }
            merge(db, vehicle.copy(obdAddress = address, updatedAt = maxOf(System.currentTimeMillis(), before + 1)))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recordSoh(id: String, value: Double, time: Long) {
        if (value.isFinite() && value in 0.0..100.0) updateSoh(store.writableDatabase, id, value, time)
    }

    companion object {
        private const val COLUMNS = "id,name,model,capacity_kwh,obd_address,archived,updated_at,soh,soh_at"
        fun newId(): String = UUID.randomUUID().toString()
        fun normalize(address: String): String = address.trim().uppercase(Locale.ROOT)
        fun validAddress(address: String): Boolean = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").matches(address)
        fun resolveObd(activeId: String, matches: List<String>): String? = when {
            matches.isEmpty() || activeId in matches -> activeId
            else -> matches.singleOrNull()
        }

        fun createSchema(db: SQLiteDatabase, legacyAddress: String?): String {
            db.execSQL("""CREATE TABLE vehicles(id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, model TEXT,
                capacity_kwh REAL, obd_address TEXT, archived INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                soh REAL, soh_at INTEGER NOT NULL DEFAULT 0)""")
            db.execSQL("CREATE INDEX vehicle_obd ON vehicles(obd_address,archived)")
            db.execSQL("""CREATE TABLE vehicle_selection(id INTEGER PRIMARY KEY CHECK(id=1),
                vehicle_id TEXT NOT NULL REFERENCES vehicles(id), initial_vehicle_id TEXT NOT NULL REFERENCES vehicles(id))""")
            val id = newId()
            val address = legacyAddress?.let(::normalize)?.takeIf(::validAddress)
            merge(db, VehicleProfile(id, "e-2008", "Peugeot e-2008", 46.0, address,
                updatedAt = if (address == null) 0 else System.currentTimeMillis()))
            db.execSQL("INSERT INTO vehicle_selection VALUES(1,?,?)", arrayOf(id, id))
            return id
        }

        fun initialId(db: SQLiteDatabase): String = db.rawQuery("SELECT initial_vehicle_id FROM vehicle_selection WHERE id=1", null)
            .use { check(it.moveToFirst()); it.getString(0) }

        fun unusedInitialId(db: SQLiteDatabase): String? = db.rawQuery("""SELECT initial_vehicle_id FROM vehicle_selection
            JOIN vehicles ON vehicles.id=initial_vehicle_id WHERE updated_at=0 AND soh_at=0
            AND (SELECT COUNT(*) FROM vehicles)=1 AND NOT EXISTS(SELECT 1 FROM trips)
            AND NOT EXISTS(SELECT 1 FROM dc_charge_samples)""", null).use { if (it.moveToFirst()) it.getString(0) else null }

        fun adoptInitial(db: SQLiteDatabase, previous: String, imported: String) {
            if (previous == imported) return
            db.execSQL("UPDATE vehicle_selection SET vehicle_id=?,initial_vehicle_id=? WHERE id=1", arrayOf(imported, imported))
            db.delete("consumption_profiles", "vehicle_id=?", arrayOf(previous))
            db.delete("consumption_model", "vehicle_id=?", arrayOf(previous))
            db.delete("vehicles", "id=?", arrayOf(previous))
        }

        fun all(db: SQLiteDatabase): List<VehicleProfile> = db.rawQuery(
            "SELECT $COLUMNS FROM vehicles ORDER BY archived,name COLLATE NOCASE,id", null
        ).use { rows -> buildList { while (rows.moveToNext()) add(fromRow(rows)) } }

        fun merge(db: SQLiteDatabase, vehicle: VehicleProfile) {
            val values = ContentValues().apply {
                put("name", vehicle.name); put("model", vehicle.model); put("capacity_kwh", vehicle.capacityKwh)
                put("obd_address", vehicle.obdAddress); put("archived", vehicle.archived); put("updated_at", vehicle.updatedAt)
            }
            if (db.update("vehicles", values, "id=? AND updated_at<?",
                    arrayOf(vehicle.id, vehicle.updatedAt.toString())) == 0) {
                values.put("id", vehicle.id)
                db.insertWithOnConflict("vehicles", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            vehicle.soh?.let { updateSoh(db, vehicle.id, it, vehicle.sohAt) }
        }

        fun repairSelection(db: SQLiteDatabase) {
            db.execSQL("""UPDATE vehicle_selection SET vehicle_id=COALESCE(
                (SELECT id FROM vehicles WHERE archived=0 ORDER BY id LIMIT 1), ?) WHERE id=1
                AND vehicle_id IN (SELECT id FROM vehicles WHERE archived=1)""", arrayOf(initialId(db)))
            db.execSQL("""UPDATE vehicles SET archived=0 WHERE archived=1 AND
                id=(SELECT vehicle_id FROM vehicle_selection WHERE id=1)""")
        }

        private fun updateSoh(db: SQLiteDatabase, id: String, value: Double, time: Long) {
            db.execSQL("UPDATE vehicles SET soh=?,soh_at=? WHERE id=? AND soh_at<?", arrayOf<Any>(value, time, id, time))
        }

        private fun fromRow(row: Cursor) = VehicleProfile(row.getString(0), row.getString(1), row.getString(2),
            if (row.isNull(3)) null else row.getDouble(3), row.getString(4), row.getInt(5) != 0, row.getLong(6),
            if (row.isNull(7)) null else row.getDouble(7), row.getLong(8))
    }
}
