package cat.ptrack

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
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
            checkSummaries(target); checkConsumption(target); checkVehicleMigration(target); checkTripBackup(target); checkChargingCatalog(target)
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
            result.putString("stream", "PASS: v1/v2/v5 to v6 migration, vehicle scope, summaries, legacy NULL, consumption, backup round trip/deduplication/rollback, charging catalog rollback, interrupted-trip recovery, SOC, PDFs in ca/es/en, rendering and activity launch\n")
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
                        val planner = ValhallaPlanner(engine, catalog, ConsumptionRepository(store).planningProfile(),
                            store.dcChargeProfile())
                        val request = ValhallaPlanner.Trip(from, to, 80.0, 10.0, 50.0,
                            ElectricRoutePlanner.Priority.FASTEST, departureMs = 0L)
                        val result = planner.alternatives(request).firstOrNull()
                        verify(result != null && result.charges.isEmpty() && result.arrivalKwh in 32.0..36.8,
                            "EV calculation with local roads")
                        verify(result?.legs?.singleOrNull()?.shape?.size ?: 0 > 2, "Geometry for the displayed route")
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
        context.openOrCreateDatabase(name, 0, null).use { db ->
            createLegacyTrips(db)
            ConsumptionRepository.createSchema(db)
            id = 1L
            db.execSQL("INSERT INTO trips VALUES(1,1000,NULL,1,'recording')")
            for (i in 0..60) db.execSQL(
                "INSERT INTO samples(trip,time,soc,voltage,current,speed,raw) VALUES(1,?,?,400,30,60,'preserved-v2')",
                arrayOf<Any>(1000 + i * 1000L, 70 - i * .001))
            db.execSQL("INSERT INTO readings(trip,time,signal,value,raw) VALUES(?,1000,'energy',32,'initial')", arrayOf(id))
            db.execSQL("INSERT INTO readings(trip,time,signal,value,raw) VALUES(?,61000,'energy',31.8,'final')", arrayOf(id))
            db.version = 2
        }
        Store(context, name).use { upgraded ->
            verify(upgraded.readableDatabase.version == 7, "v2 to v7")
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
    private fun createLegacyTrips(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)")
        db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)")
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)")
        db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)")
    }

    private fun checkVehicleMigration(context: Context) {
        val name = "vehicle-v5-smoke.db"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("vehicle-migration-$name", mode)
        }
        context.deleteDatabase(name)
        val settings = isolated.getSharedPreferences("settings", 0)
        settings.edit().putString("address", "ab:cd:ef:12:34:56").commit()
        try {
            context.openOrCreateDatabase(name, 0, null).use { db ->
                createLegacyTrips(db)
                ConsumptionRepository.createSchema(db)
                db.execSQL("CREATE TABLE trip_summaries(trip INTEGER PRIMARY KEY, version INTEGER NOT NULL, stats TEXT NOT NULL)")
                db.execSQL("CREATE TABLE trip_backup_keys(trip INTEGER PRIMARY KEY, backup_key TEXT NOT NULL UNIQUE)")
                db.execSQL("CREATE TABLE dc_charge_samples(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, session INTEGER NOT NULL, soc REAL NOT NULL, kw REAL NOT NULL, pack REAL NOT NULL, ambient REAL NOT NULL)")
                db.execSQL("INSERT INTO trips VALUES(7,1000,3000,0,'complete'),(8,4000,5000,1,'complete')")
                db.execSQL("INSERT INTO readings(trip,time,signal,value) VALUES(7,2000,'soh',88),(8,4500,'soh',96)")
                db.execSQL("INSERT INTO dc_charge_samples VALUES(12,4000,3000,25,80,20,18)")
                db.version = 5
            }
            Store(isolated, name).use { store ->
                val vehicle = store.vehicles.active()
                verify(vehicle.id == VehicleProfiles.initialId(store.readableDatabase) && vehicle.capacityKwh == 46.0 &&
                    vehicle.obdAddress == "AB:CD:EF:12:34:56" && vehicle.soh == 88.0 && vehicle.sohAt == 2000L, "Migrate MAC and latest real SOH")
                store.tripsBetween(0, 10_000, vehicle.id).use { verify(it.count == 2, "Migrate trips into initial vehicle") }
                store.readableDatabase.rawQuery("SELECT id,vehicle_id FROM dc_charge_samples", null).use {
                    verify(it.moveToFirst() && it.getLong(0) == 12L && it.getString(1) == vehicle.id, "Migrate DC observation intact")
                }
                val second = VehicleProfile(VehicleProfiles.newId(), "Another car", null, null, vehicle.obdAddress)
                store.vehicles.save(second)
                verify(store.vehicles.select(second.id), "Select second vehicle")
                store.vehicles.save(vehicle.copy(archived = true))
                verify(store.vehicles.matches(vehicle.obdAddress!!).map { it.id } == listOf(second.id), "Ignore archived OBD links")
                verify(store.tripVehicle(7) == vehicle.name, "Keep history of archived vehicles")
            }
        } finally {
            settings.edit().clear().commit()
            context.deleteDatabase(name)
        }
    }

    private fun checkConsumption(context: Context) {
        val name = "consumption-smoke.db"; context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            createLegacyTrips(db)
            db.execSQL("INSERT INTO trips VALUES(1,1000,2000,0,'complete')")
            db.execSQL("INSERT INTO samples(trip,time,raw) VALUES(1,1000,'preserve-null')")
            db.execSQL("INSERT INTO readings(trip,time,raw) VALUES(1,1000,'preserve-reading')"); db.version = 1
        }
        Store(context, name).use { store ->
            val db = store.writableDatabase
            verify(db.version == 7, "v1 to v7")
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
            val other = VehicleProfile(VehicleProfiles.newId(), "Second car", null, 65.0, null)
            store.vehicles.save(other)
            verify(ConsumptionRepository(store, vehicleId = other.id).estimate(60.0, ConsumptionModel.Terrain.UNKNOWN).trips == 0,
                "Learning ignores the first vehicle's trips")
            verify(ConsumptionRepository(store, vehicleId = other.id).planningProfile(other.capacityKwh).capacityKwh == 65.0,
                "Planner uses the selected vehicle's reference capacity")
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
        val freshName = "backup-fresh-smoke.db"
        context.deleteDatabase(sourceName)
        context.deleteDatabase(targetName)
        context.deleteDatabase(freshName)
        val backup = File.createTempFile("ptrack-smoke-", ".gz", context.cacheDir)
        val automatic = File.createTempFile("ptrack-smoke-auto-", ".gz", context.cacheDir)
        val truncated = File.createTempFile("ptrack-smoke-truncated-", ".gz", context.cacheDir)
        val legacy = File.createTempFile("ptrack-smoke-legacy-", ".gz", context.cacheDir)
        val vehicle = VehicleProfile(VehicleProfiles.newId(), "Second e-2008", "e-2008", 52.0, "12:34:56:78:90:AB",
            maxDcChargeKw = 100.0)
        try {
            GZIPOutputStream(legacy.outputStream()).bufferedWriter().use {
                it.write("""{"format":"PTrack trip backup","version":1,"trips":[["00000000000000000000000000000001",[1000,2000,0,"complete"],[],[],[]]]}""")
            }
            Store(context, sourceName).use { source ->
                source.vehicles.save(vehicle)
                verify(source.vehicles.select(vehicle.id), "Select a new profile")
                val id = source.begin(false)
                source.add(id, Sample(1000, 70.0, 400.0, 30.0, 60.0, 41.0, 2.0, null, "original sample"))
                source.writableDatabase.execSQL("INSERT INTO readings(trip,time,signal,raw) VALUES(?,1000,'energy','raw reading')", arrayOf(id))
                source.finish(id)
                source.begin(true)
                source.vehicles.recordSoh(vehicle.id, 86.0, 1000)
                for (session in listOf(1_000L, 100_000L)) for (offset in listOf(0L, 10_000L, 20_000L)) {
                    source.addDcCharge(DcChargeProfile.Observation(session, session + offset, 50.0, 83.0, 20.0, 19.0))
                }
            }
            Store(context, sourceName).use { source ->
                verify(source.vehicles.active().id == vehicle.id, "Active vehicle persists on reopen")
                val revision = TripBackup.write(source, automatic, automatic = true)
                verify(revision != null, "Automatic backup written")
                verify(TripBackup.write(source, automatic, automatic = true, previous = revision) == null, "Unchanged history skipped")
                source.vehicles.save(source.vehicles.active().copy(name = "Renamed e-2008"))
                verify(TripBackup.write(source, automatic, automatic = true, previous = revision) != null, "Profile-only changes backed up")
                TripBackup.write(source, backup)
            }
            val bytes = backup.readBytes()
            truncated.writeBytes(bytes.copyOf(bytes.size - 5))
            Store(context, freshName).use { fresh ->
                val placeholder = fresh.vehicles.active().id
                verify(TripBackup.read(fresh, backup) == 2, "Restore a backup into an empty database")
                verify(fresh.vehicles.list().size == 2 && fresh.vehicles.find(placeholder) == null,
                    "Replace only the untouched local placeholder")
            }
            Store(context, targetName).use { target ->
                val existing = target.begin(false)
                target.finish(existing)
                verify(runCatching { TripBackup.read(target, truncated) }.isFailure, "Reject truncated backup")
                target.trips().use { verify(it.count == 1, "Failed import rolls back all inserted trips") }
                verify(target.vehicles.list().size == 1, "Failed import rolls back vehicles")
                verify(TripBackup.read(target, automatic) == 1, "Automatic snapshot excludes open trips")
                verify(TripBackup.read(target, backup) == 1, "Manual snapshot restores the interrupted trip without duplicating the completed one")
                verify(TripBackup.read(target, backup) == 0, "Repeat import does not duplicate")
                target.trips().use { verify(it.count == 3, "Keep existing trip despite conflicting local ID") }
                verify(target.vehicles.matches(vehicle.obdAddress!!.lowercase()).single().let {
                    it.id == vehicle.id && it.name == "Renamed e-2008" && it.soh == 86.0 &&
                        it.maxDcChargeKw == vehicle.maxDcChargeKw
                }, "Restore profile, MAC, SOH, and maximum DC power")
                target.tripsBetween(0, Long.MAX_VALUE, vehicle.id).use { verify(it.count == 2, "Restored trips scoped to their vehicle") }
                verify(target.dcChargeProfile(vehicle.id).curve(19.0)[10] == 83.0 && !target.dcChargeProfile().hasData,
                    "Charge learning scoped to restored vehicle")
                target.readableDatabase.rawQuery("SELECT COUNT(*) FROM dc_charge_samples", null).use {
                    verify(it.moveToFirst() && it.getInt(0) == 6, "Charge backup import is idempotent")
                }
                verify(TripBackup.read(target, legacy) == 1, "Read version 1 backup")
                target.tripsBetween(0, Long.MAX_VALUE, VehicleProfiles.initialId(target.readableDatabase)).use {
                    verify(it.count == 2, "Version 1 trip belongs to the initial profile")
                }
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
            legacy.delete()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
            context.deleteDatabase(freshName)
        }
    }
}
