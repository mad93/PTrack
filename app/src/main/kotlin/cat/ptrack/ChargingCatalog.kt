package cat.ptrack

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.InputStream
import java.io.InterruptedIOException

internal class ChargingCatalog(context: Context, name: String = "charging.db") : SQLiteOpenHelper(context, name, null, 1) {
    init { setWriteAheadLoggingEnabled(true) }

    data class Snapshot(val checkedAt: Long, val publishedAt: Long, val sites: Int, val connectors: Int)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sites(id TEXT PRIMARY KEY NOT NULL, name TEXT, operator TEXT, hours TEXT, lat REAL NOT NULL, lon REAL NOT NULL)")
        db.execSQL("CREATE INDEX sites_location ON sites(lat, lon)")
        db.execSQL("CREATE TABLE connectors(site_id TEXT NOT NULL, station_id TEXT NOT NULL, point_id TEXT NOT NULL, type TEXT NOT NULL, mode TEXT, power_kw REAL)")
        db.execSQL("CREATE INDEX connectors_site ON connectors(site_id)")
        db.execSQL("CREATE TABLE publication(checked_at INTEGER NOT NULL, published_at INTEGER NOT NULL, sites INTEGER NOT NULL, connectors INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun snapshot(): Snapshot? = readableDatabase.rawQuery(
        "SELECT checked_at, published_at, sites, connectors FROM publication", null
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else Snapshot(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getInt(3))
    }

    fun fastChargers(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, minimumKw: Double): List<ElectricRoutePlanner.Stop> =
        readableDatabase.rawQuery(
            """SELECT sites.lat, sites.lon, COALESCE(sites.name, sites.id), MAX(connectors.power_kw)
               FROM sites JOIN connectors ON connectors.site_id = sites.id
               WHERE sites.lat BETWEEN ? AND ? AND sites.lon BETWEEN ? AND ?
                 AND connectors.type = 'iec62196T2COMBO' AND connectors.power_kw >= ?
               GROUP BY sites.id""",
            arrayOf(minLat, maxLat, minLon, maxLon, minimumKw).map(Double::toString).toTypedArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ElectricRoutePlanner.Stop(
                    ElectricRoutePlanner.Point(cursor.getDouble(0), cursor.getDouble(1)),
                    cursor.getString(2), cursor.getDouble(3)))
            }
        }

    fun replace(input: InputStream) {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val previous = snapshot()
            db.delete("connectors", null, null)
            db.delete("sites", null, null)
            val publication = db.compileStatement("INSERT INTO sites VALUES(?, ?, ?, ?, ?, ?)").use { site ->
                db.compileStatement("INSERT INTO connectors VALUES(?, ?, ?, ?, ?, ?)").use { connector ->
                    ChargingFeed.read(input) { item ->
                        site.bindString(1, item.id)
                        site.bindText(2, item.name)
                        site.bindText(3, item.operator)
                        site.bindText(4, item.hours)
                        site.bindDouble(5, item.latitude)
                        site.bindDouble(6, item.longitude)
                        site.executeInsert()
                        for (plug in item.connectors) {
                            connector.bindString(1, item.id)
                            connector.bindString(2, plug.stationId)
                            connector.bindString(3, plug.pointId)
                            connector.bindString(4, plug.type)
                            connector.bindText(5, plug.mode)
                            if (plug.powerKw == null) connector.bindNull(6) else connector.bindDouble(6, plug.powerKw)
                            connector.executeInsert()
                        }
                    }
                }
            }
            require(previous == null || publication.time >= previous.publishedAt) { "Older charging publication" }
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            db.delete("publication", null, null)
            db.execSQL("INSERT INTO publication VALUES(?, ?, ?, ?)",
                arrayOf<Any>(System.currentTimeMillis(), publication.time, publication.sites, publication.connectors))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun android.database.sqlite.SQLiteStatement.bindText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindString(index, value)
    }
}
