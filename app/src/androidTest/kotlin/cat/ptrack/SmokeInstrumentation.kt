package cat.ptrack

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** On-device regression checks using isolated databases and report files. */
class SmokeInstrumentation : Instrumentation() {
    private var offlineRoutingOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        offlineRoutingOnly = arguments?.getString("offlineRouting") == "true"
        start()
    }
    private fun verify(condition: Boolean, message: String) { if (!condition) throw AssertionError(message) }
    override fun onStart() {
        val result = Bundle()
        try {
            val target = targetContext
            if (offlineRoutingOnly) {
                checkOfflineRouting(target)
                result.putString("stream", "PASS: native offline routing, elevation, EV route and failed map replacement\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            checkSummaries(target); checkConsumption(target); checkTripBackup(target); checkChargingCatalog(target)
            val isolated = object : ContextWrapper(target) {
                override fun getFilesDir(): File = File(super.getCacheDir(), "smoke-reports").apply { mkdirs() }
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("smoke-$name", mode)
            }
            target.deleteDatabase("smoke.db")
            val profile = Profile(isolated)
            verify(profile.signals.containsKey("current") && profile.signals.containsKey("soc") && profile.rawSignals.isNotEmpty(), "Bundled vehicle profile")
            val id: Long
            Store(isolated, "smoke.db").use { store ->
                id = store.begin(true); val db = store.writableDatabase; db.beginTransaction()
                try {
                    val epoch = 1730000000000L
                    for (i in 0 until 720) store.add(id, Sample(epoch + i * 2000, 60 - i * 20.0 / 719, 370.0, 100 * sin(i / 23.0),
                        60 + 20 * sin(i / 31.0), 41.0, 2.0, 150 + 40 * sin(i / 53.0), "{\"simulation\":true}"))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                store.finish(id)
            }
            Store(isolated, "smoke.db").use { store ->
                verify(store.samples(id).size == 720, "SQLite persistence")
                val stats = TripStats(); store.samples(id).forEach(stats::add); verify(abs(stats.endSoc - 40) < .001, "Final SOC")
                val interrupted = store.begin(true); store.recover()
                store.trips().use { verify(it.moveToFirst() && it.getLong(0) == interrupted && it.getString(4) == "interrupted", "Crash recovery") }
                for (language in arrayOf("ca", "es", "en")) {
                    isolated.getSharedPreferences("settings", 0).edit().putString("lang", language).commit()
                    val pdf = Report(isolated).generate(store, id); verify(pdf.length() > 1000, "PDF content")
                    FileInputStream(pdf).use { val header = ByteArray(5); verify(it.read(header) == 5 && header.toString(Charsets.US_ASCII) == "%PDF-", "PDF header") }
                    ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            verify(renderer.pageCount == 2, "PDF page count")
                            for (p in 0..1) renderer.openPage(p).use { page ->
                                verify(page.width == 595 && page.height == 842, "A4 size")
                                val image = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
                                image.eraseColor(Color.WHITE); page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                FileOutputStream(File(isolated.filesDir, "report-$language-${p + 1}.png")).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                image.recycle()
                            }
                        }
                    }
                }
            }
            val activity = startActivitySync(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync(); verify(activity != null, "Activity launch"); runOnMainSync { activity.finish() }
            result.putString("stream", "PASS: v1/v2/v3 to v4 migration, summaries, legacy NULL, consumption, backup round trip/deduplication/rollback, charging catalog rollback, interrupted-trip recovery, SOC, PDFs in ca/es/en, rendering and activity launch\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) { result.putString("stream", "FAIL: $failure\n"); finish(Activity.RESULT_CANCELED, result) }
    }

    private fun checkOfflineRouting(context: Context) {
        val archive = File(context.cacheDir, "offline-routing-test.zip")
        val privateFiles = File(context.cacheDir, "offline-routing-smoke").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = privateFiles
        }
        try {
            archive.inputStream().use { OfflineValhalla.install(isolated, it) }
            verify(OfflineValhalla.installed(isolated), "Activate pilot map")
            verify(runCatching { OfflineValhalla.install(isolated, "broken".byteInputStream()) }.isFailure,
                "Reject invalid replacement")
            val from = ElectricRoutePlanner.Point(35.8895, -5.3190)
            val to = ElectricRoutePlanner.Point(35.9143, -5.3688)
            OfflineValhalla.withEngine(isolated) { engine ->
                val trip = org.json.JSONObject(engine.routeRaw(ValhallaPlanner.request(from, to))).getJSONObject("trip")
                val distance = trip.getJSONObject("summary").getDouble("length")
                val heights = trip.getJSONArray("legs").getJSONObject(0).getJSONArray("elevation")
                verify(distance in 6.0..9.0 && heights.length() > 100, "Pilot route distance and elevations")
                Store(isolated, "offline-routing-smoke.db").use { store ->
                    ChargingCatalog(isolated, "offline-routing-charging-smoke.db").use { catalog ->
                        val planner = ValhallaPlanner(engine, catalog, ConsumptionRepository(store).planningProfile(), 18.0)
                        val result = planner.plan(from, to, 45.0, 80.0, 10.0, 50.0, 100.0,
                            ElectricRoutePlanner.Priority.FASTEST)
                        verify(result != null && result.charges.isEmpty() && result.arrivalKwh in 32.0..36.0,
                            "EV calculation with local roads")
                    }
                }
            }
        } finally {
            privateFiles.deleteRecursively()
            context.deleteDatabase("offline-routing-smoke.db")
            context.deleteDatabase("offline-routing-charging-smoke.db")
        }
    }

    private fun checkChargingCatalog(context: Context) {
        val name = "charging-smoke.db"
        context.deleteDatabase(name)
        val xml = """
            <payload>
              <publicationTime>2026-11-07T10:16:37+01:00</publicationTime>
              <energyInfrastructureTable><energyInfrastructureSite id="initial">
                <locationReference><coordinatesForDisplay>
                  <latitude>41.4</latitude><longitude>2.2</longitude>
                </coordinatesForDisplay></locationReference>
                <energyInfrastructureStation id="station"><refillPoint id="point">
                  <connector><connectorType>iec62196T2COMBO</connectorType></connector>
                </refillPoint></energyInfrastructureStation>
              </energyInfrastructureSite></energyInfrastructureTable>
            </payload>
        """.trimIndent()
        try {
            ChargingCatalog(context, name).use { catalog ->
                catalog.replace(xml.byteInputStream())
                val original = checkNotNull(catalog.snapshot())
                val truncated = xml.replace("initial", "incomplete").substringBefore("</payload>")
                verify(runCatching { catalog.replace(truncated.byteInputStream()) }.isFailure, "Reject truncated catalog")
                verify(catalog.snapshot() == original, "Keep charging publication on failed import")
            }
            ChargingCatalog(context, name).use { catalog ->
                catalog.readableDatabase.rawQuery("SELECT id FROM sites", null).use {
                    verify(it.moveToFirst() && it.getString(0) == "initial" && !it.moveToNext(), "Keep original site rows on failed import")
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
    private fun checkSummaries(context: Context) {
        val name = "summary-v2-smoke.db"; context.deleteDatabase(name)
        val id: Long
        Store(context, name).use { legacy ->
            id = legacy.begin(true)
            for (i in 0..60) legacy.add(id, Sample(1000 + i * 1000L, 70 - i * .001, 400.0, 30.0, 60.0, null, null, null, "preserved-v2"))
            val db = legacy.writableDatabase
            db.execSQL("INSERT INTO readings(trip,time,signal,value,raw) VALUES(?,1000,'energy',32,'initial')", arrayOf(id))
            db.execSQL("INSERT INTO readings(trip,time,signal,value,raw) VALUES(?,61000,'energy',31.8,'final')", arrayOf(id))
            db.execSQL("DROP TABLE trip_summaries")
            db.execSQL("DROP TABLE trip_backup_keys")
            db.version = 2
        }
        Store(context, name).use { upgraded ->
            verify(upgraded.readableDatabase.version == 4, "v2 to v4")
            verify(upgraded.samples(id).size == 61 && upgraded.samples(id).first().raw == "preserved-v2", "v2 samples intact")
            upgraded.finish(id)
            upgraded.readableDatabase.rawQuery("SELECT version FROM trip_summaries WHERE trip=?", arrayOf(id.toString())).use {
                verify(it.moveToFirst() && it.getInt(0) == 2, "Summary automatically persisted on finish")
            }
        }
        Store(context, name).use { reopened ->
            val s = reopened.summary(id); verify(s.count == 61L && s.durationMs == 60000L, "Persisted duration/count")
            verify(abs(s.consumed - .2) < 1e-8 && abs(s.maxDischargeKw - 12) < 1e-8, "Persisted energy/peaks")
            verify(abs(s.startEnergy - 32) < 1e-8 && abs(s.endEnergy - 31.8) < 1e-8 && abs(s.rangeKm - 159) < 1e-8, "Persisted BMS endpoints/range")
            reopened.writableDatabase.delete("trip_summaries", "trip=?", arrayOf(id.toString()))
            verify(reopened.summary(id).count == 61L, "Legacy summary derived from original samples")
            val empty = reopened.begin(false)
            reopened.writableDatabase.execSQL("INSERT INTO samples(trip,time,raw) VALUES(?,1000,'unknown')", arrayOf(empty))
            reopened.finish(empty); val unknown = reopened.summary(empty)
            verify(unknown.count == 0L && unknown.maxDischargeKw.isNaN() && unknown.startEnergy.isNaN(), "SQL NULL not invented zero")
        }
        context.deleteDatabase(name)
    }
    private fun checkConsumption(context: Context) {
        val name = "consumption-smoke.db"; context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)")
            db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)")
            db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)")
            db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)")
            db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)")
            db.execSQL("INSERT INTO trips VALUES(1,1000,2000,0,'complete')")
            db.execSQL("INSERT INTO samples(trip,time,raw) VALUES(1,1000,'preserve-null')")
            db.execSQL("INSERT INTO readings(trip,time,raw) VALUES(1,1000,'preserve-reading')"); db.version = 1
        }
        Store(context, name).use { store ->
            val db = store.writableDatabase
            verify(db.version == 4, "v1 to v4")
            db.rawQuery("SELECT soc,raw FROM samples WHERE trip=1", null).use { verify(it.moveToFirst() && it.isNull(0) && it.getString(1) == "preserve-null", "Migration preserves samples and SQL NULL") }
            db.rawQuery("SELECT raw FROM readings", null).use { verify(it.moveToFirst() && it.getString(0) == "preserve-reading", "Migration preserves readings") }
            for (t in 0 until 5) {
                val trip = store.begin(t == 3)
                for (i in 0..120) {
                    val km = 60 * i * 5 / 3600.0
                    store.add(trip, Sample(1000 + i * 5000L, 60.0, 400.0, 30.0, 60.0, km / 6371 * 180 / PI, 0.0, null, "original"))
                }
                if (t != 4) store.finish(trip)
            }
            val repository = ConsumptionRepository(store)
            val estimate = repository.estimate(60.0, ConsumptionModel.Terrain.UNKNOWN)
            verify(estimate.trips == 3 && estimate.validIntervals == 360L, "Only completed real trips qualify")
            verify(abs(checkNotNull(estimate.kwhPer100Km) - 20) < 1e-8 && abs(estimate.distanceKm - 30) < 1e-8, "Persisted observed consumption")
            repository.recalculate(); verify(repository.estimate(60.0, ConsumptionModel.Terrain.UNKNOWN).validIntervals == 360L, "Rebuild is idempotent")
            verify(repository.estimate(100.0, ConsumptionModel.Terrain.UNKNOWN).kwhPer100Km == null, "No fabricated speed estimate")
            db.rawQuery("SELECT id FROM trips WHERE status='recording'", null).use { verify(it.moveToFirst(), "Open trip"); store.finish(it.getLong(0)) }
            verify(repository.estimate(60.0, ConsumptionModel.Terrain.UNKNOWN).trips == 4, "New history refresh")
            verify(ConsumptionRepository(store, 20).estimate(60.0, ConsumptionModel.Terrain.UNKNOWN).trips == 4, "Rebinning")
            db.rawQuery("SELECT COUNT(*) FROM samples WHERE raw='original'", null).use { verify(it.moveToFirst() && it.getInt(0) == 605, "Analysis preserves originals") }
        }
        Store(context, name).use { store -> store.readableDatabase.rawQuery("SELECT km FROM consumption_profiles", null).use {
            verify(it.moveToFirst() && abs(it.getDouble(0) - 40) < 1e-8, "Learned profile survives reopen")
        } }
        context.deleteDatabase(name)
    }

    private fun checkTripBackup(context: Context) {
        val sourceName = "backup-source-smoke.db"
        val targetName = "backup-target-smoke.db"
        context.deleteDatabase(sourceName)
        context.deleteDatabase(targetName)
        val backup = File.createTempFile("ptrack-smoke-", ".gz", context.cacheDir)
        val automatic = File.createTempFile("ptrack-smoke-auto-", ".gz", context.cacheDir)
        val truncated = File.createTempFile("ptrack-smoke-truncated-", ".gz", context.cacheDir)
        try {
            Store(context, sourceName).use { source ->
                val id = source.begin(false)
                source.add(id, Sample(1000, 70.0, 400.0, 30.0, 60.0, 41.0, 2.0, null, "original sample"))
                source.writableDatabase.execSQL("INSERT INTO readings(trip,time,signal,raw) VALUES(?,1000,'energy','raw reading')", arrayOf(id))
                source.finish(id)
                source.begin(true)
                source.writableDatabase.execSQL("DROP TABLE trip_backup_keys")
                source.writableDatabase.version = 3
            }
            Store(context, sourceName).use { source ->
                verify(source.readableDatabase.version == 4, "v3 to v4")
                val revision = TripBackup.write(source, automatic, automatic = true)
                verify(revision != null, "Automatic backup written")
                verify(TripBackup.write(source, automatic, automatic = true, previous = revision) == null, "Unchanged history skipped")
                TripBackup.write(source, backup)
            }
            val bytes = backup.readBytes()
            truncated.writeBytes(bytes.copyOf(bytes.size - 5))
            Store(context, targetName).use { target ->
                val existing = target.begin(false)
                target.finish(existing)
                verify(runCatching { TripBackup.read(target, truncated) }.isFailure, "Reject truncated backup")
                target.trips().use { verify(it.count == 1, "Failed import rolls back all inserted trips") }
                verify(TripBackup.read(target, automatic, automatic = true) == 1, "Automatic snapshot excludes open trips")
                verify(TripBackup.read(target, backup) == 1, "Manual snapshot restores the interrupted trip without duplicating the completed one")
                verify(TripBackup.read(target, backup) == 0, "Repeat import does not duplicate")
                target.trips().use { verify(it.count == 3, "Keep existing trip despite conflicting local ID") }
                target.readableDatabase.rawQuery("SELECT trip,value,raw FROM readings", null).use {
                    verify(it.moveToFirst() && it.getLong(0) != existing && it.isNull(1) && it.getString(2) == "raw reading", "Raw readings and NULL restored")
                    val restored = it.getLong(0)
                    val sample = target.samples(restored).single()
                    verify(sample.raw == "original sample" && sample.latitude == 41.0 && sample.altitude == null, "Samples and GPS restored")
                    verify(target.summary(restored).count == 1L, "Summary restored")
                }
            }
        } finally {
            backup.delete()
            automatic.delete()
            truncated.delete()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }
}
