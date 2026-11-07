package cat.ptrack

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal object ChargingSync {
    const val REVISION = "revision"
    private const val FAILED = "failed"
    private const val MANUAL = 7411
    private const val WEEKLY = 7412
    private const val REQUESTED_AT = "requested_at"
    private const val FEED = "https://nap.dgt.es/datex2/v3/miterd/EnergyInfrastructureTablePublication/electrolineras.xml"
    @Volatile private var updating = false

    enum class State { IDLE, QUEUED, UPDATING, FAILED }

    fun prefs(context: Context) = context.getSharedPreferences("charging_sync", Context.MODE_PRIVATE)

    fun state(context: Context): State = when {
        updating -> State.UPDATING
        scheduler(context).getPendingJob(MANUAL) != null -> State.QUEUED
        prefs(context).getBoolean(FAILED, false) -> State.FAILED
        else -> State.IDLE
    }

    fun schedule(context: Context) {
        val scheduler = scheduler(context)
        if (scheduler.getPendingJob(WEEKLY) == null) {
            val result = scheduler.schedule(job(context, WEEKLY).setPeriodic(TimeUnit.DAYS.toMillis(7)).build())
            if (result == JobScheduler.RESULT_SUCCESS) request(context)
        }
    }

    fun request(context: Context): Boolean {
        val scheduler = scheduler(context)
        if (updating || scheduler.getPendingJob(MANUAL) != null) return true
        val extras = PersistableBundle().apply { putLong(REQUESTED_AT, System.currentTimeMillis()) }
        val scheduled = scheduler.schedule(job(context, MANUAL).setExtras(extras)
            .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()) == JobScheduler.RESULT_SUCCESS
        if (scheduled) signal(context)
        return scheduled
    }

    fun run(context: Context, params: JobParameters): Boolean {
        updating = true
        signal(context)
        var failed = false
        try {
            ChargingCatalog(context).use { catalog ->
                val last = catalog.snapshot()?.checkedAt ?: 0L
                val minimum = if (params.jobId == MANUAL) params.extras.getLong(REQUESTED_AT)
                    else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6)
                if (last < minimum) download(catalog)
            }
        } catch (error: Exception) {
            failed = true
            Log.w("PTrackCharging", "NAP refresh failed", error)
            return error is IOException
        } finally {
            updating = false
            prefs(context).edit().putBoolean(FAILED, failed).apply()
            signal(context)
        }
        return false
    }

    fun signal(context: Context) {
        prefs(context).edit().putLong(REVISION, System.nanoTime()).apply()
    }

    private fun download(catalog: ChargingCatalog) {
        val connection = (URL(FEED).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
        }
        try {
            val code = connection.responseCode
            if (code == 429 || code in 500..599) throw IOException("NAP HTTP $code")
            check(code == 200) { "NAP HTTP $code" }
            connection.inputStream.buffered().use(catalog::replace)
        } finally {
            connection.disconnect()
        }
    }

    private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)

    private fun job(context: Context, id: Int) = JobInfo.Builder(id, ComponentName(context, ChargingJob::class.java))
        .setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
}

class ChargingJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private val jobs = mutableMapOf<Int, Pair<JobParameters, Future<*>>>()

    override fun onStartJob(params: JobParameters): Boolean {
        val task = worker.submit {
            val retry = ChargingSync.run(applicationContext, params)
            main.post {
                if (jobs[params.jobId]?.first === params) {
                    jobs.remove(params.jobId)
                    jobFinished(params, retry)
                    ChargingSync.signal(applicationContext)
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
