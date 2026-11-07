package cat.ptrack

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal object BackupSync {
    const val LAST_BACKUP = "last_backup"
    const val HISTORY_CHANGED = "history_changed"
    private const val DESTINATION = "destination"
    private const val SOURCES = "sources"
    private const val REVISION = "revision"
    private const val NOW = 7401
    private const val PERIODIC = 7402
    private const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun prefs(context: Context) = context.getSharedPreferences("backup_sync", Context.MODE_PRIVATE)
    fun destination(context: Context) = prefs(context).getString(DESTINATION, null)
    fun sources(context: Context) = prefs(context).getStringSet(SOURCES, emptySet()).orEmpty()

    @Synchronized
    fun link(context: Context, uri: Uri, asDestination: Boolean) {
        val resolver = context.contentResolver
        val preferences = prefs(context)
        val selected = uri.toString()
        if (asDestination) {
            val old = destination(context)
            if (old != selected) {
                resolver.takePersistableUriPermission(uri, WRITE)
                preferences.edit().putString(DESTINATION, selected).remove(REVISION).apply()
                if (old != null) release(context, old, WRITE)
            }
        } else if (selected != destination(context) && selected !in sources(context)) {
            resolver.takePersistableUriPermission(uri, READ)
            preferences.edit().putStringSet(SOURCES, sources(context) + selected).apply()
        }
        schedule(context)
    }

    @Synchronized
    fun unlink(context: Context) {
        val old = destination(context)
        val subscribed = sources(context)
        prefs(context).edit().remove(DESTINATION).remove(SOURCES).remove(REVISION).apply()
        context.getSystemService(JobScheduler::class.java)?.apply { cancel(NOW); cancel(PERIODIC) }
        if (old != null) release(context, old, WRITE)
        for (uri in subscribed) release(context, uri, READ)
    }

    private fun release(context: Context, uri: String, flags: Int) {
        try { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), flags) }
        catch (_: SecurityException) { /* The provider may already have revoked access. */ }
    }

    @Synchronized
    fun schedule(context: Context) {
        if (destination(context) == null && sources(context).isEmpty()) return
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val service = ComponentName(context, BackupJob::class.java)
            fun job(id: Int) = JobInfo.Builder(id, service).setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            if (scheduler.getPendingJob(PERIODIC) == null) {
                scheduler.schedule(job(PERIODIC).setPeriodic(TimeUnit.HOURS.toMillis(1)).build())
            }
            if (scheduler.getPendingJob(NOW) == null) {
                scheduler.schedule(job(NOW).setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
            }
        } catch (error: RuntimeException) {
            Log.w("PTrackBackup", "Could not schedule backup", error)
        }
    }

    /** Each device writes only its own document. Imports merge trips by their stable backup keys. */
    fun run(context: Context): Boolean {
        // Finishing the active trip schedules the next attempt.
        if (RecordingService.activeTrip >= 0) return false
        var retry = false
        val preferences = prefs(context)
        Store(context).use { store ->
            for (source in sources(context)) {
                if (Thread.currentThread().isInterrupted) return true
                try {
                    withTemporaryFile(context) { file ->
                        val input = context.contentResolver.openInputStream(Uri.parse(source)) ?: throw IOException("Sync source unavailable")
                        input.use { stream -> file.outputStream().use { Streams.copy(stream, it) } }
                        // A newly linked device may not have published its first trip yet.
                        if (source in sources(context) && file.length() > 0 && !Thread.currentThread().isInterrupted &&
                            TripBackup.read(store, file, automatic = true) > 0
                        ) preferences.edit().putLong(HISTORY_CHANGED, System.currentTimeMillis()).apply()
                    }
                } catch (_: TripBackup.RecordingInProgress) {
                    return false
                } catch (error: Exception) {
                    retry = true
                    Log.w("PTrackBackup", "Could not read a sync source", error)
                }
            }
            val target = destination(context)
            if (target != null && !Thread.currentThread().isInterrupted) {
                try {
                    withTemporaryFile(context) { file ->
                        val revision = TripBackup.write(store, file, automatic = true, previous = preferences.getString(REVISION, null))
                        if (revision != null && target == destination(context) && !Thread.currentThread().isInterrupted) {
                            val output = context.contentResolver.openOutputStream(Uri.parse(target), "wt")
                                ?: throw IOException("Sync destination unavailable")
                            output.use { stream -> file.inputStream().use { Streams.copy(it, stream) } }
                            markPublished(context, target, revision)
                        }
                    }
                } catch (_: TripBackup.RecordingInProgress) {
                    return false
                } catch (error: Exception) {
                    retry = true
                    Log.w("PTrackBackup", "Could not write automatic backup", error)
                }
            }
        }
        return retry
    }

    @Synchronized
    private fun markPublished(context: Context, target: String, revision: String) {
        if (target == destination(context)) prefs(context).edit().putString(REVISION, revision)
            .putLong(LAST_BACKUP, System.currentTimeMillis()).apply()
    }

    private fun withTemporaryFile(context: Context, action: (File) -> Unit) {
        val file = File.createTempFile("ptrack-sync-", ".gz", context.cacheDir)
        try { action(file) } finally { file.delete() }
    }
}

class BackupJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private val jobs = mutableMapOf<Int, Pair<JobParameters, Future<*>>>()

    override fun onStartJob(params: JobParameters): Boolean {
        val task = worker.submit {
            val retry = try { BackupSync.run(applicationContext) } catch (error: Exception) {
                Log.w("PTrackBackup", "Automatic backup failed", error)
                true
            }
            main.post {
                if (jobs[params.jobId]?.first === params) {
                    jobs.remove(params.jobId)
                    jobFinished(params, retry)
                }
            }
        }
        jobs[params.jobId] = params to task
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.second?.cancel(true)
        return true
    }

    override fun onDestroy() {
        jobs.values.forEach { it.second.cancel(true) }
        jobs.clear()
        super.onDestroy()
    }

    private companion object {
        val worker = Executors.newSingleThreadExecutor()
    }
}
