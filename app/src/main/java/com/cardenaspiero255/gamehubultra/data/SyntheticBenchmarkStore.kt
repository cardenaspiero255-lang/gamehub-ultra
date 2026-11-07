package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkContext
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkRun
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkScenario
import org.json.JSONArray
import org.json.JSONObject

/**
 * CAR-62. Persist only public device/scenario context, profile enum and measured durations.
 * No account identifiers, package lists, tokens, profile notes or remote API calls.
 */
internal class SyntheticBenchmarkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "gamehub_ultra_synthetic_benchmarks", Context.MODE_PRIVATE
    )

    fun readAll(): List<SyntheticBenchmarkRun> {
        val raw = prefs.getString("history_v1", null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schema") != 1) return emptyList()
            val runs = root.optJSONArray("runs") ?: return emptyList()
            buildList {
                for (index in 0 until runs.length()) {
                    val entry = runs.optJSONObject(index) ?: continue
                    decode(entry)?.let { add(it) }
                }
            }.takeLast(12)
        }.getOrDefault(emptyList())
    }

    fun append(run: SyntheticBenchmarkRun) {
        if (!valid(run)) return
        val bounded = (readAll() + run).takeLast(12)
        val payload = JSONObject().put("schema", 1).put(
            "runs", JSONArray().apply { bounded.forEach { put(encode(it)) } }
        )
        check(prefs.edit().putString("history_v1", payload.toString()).commit()) {
            "Cannot persist synthetic benchmark history"
        }
    }

    fun clear() {
        check(prefs.edit().remove("history_v1").commit()) {
            "Cannot clear synthetic benchmark history"
        }
    }

    private fun valid(run: SyntheticBenchmarkRun): Boolean =
        run.profileName in PerformanceProfile.entries.map { it.name } &&
            run.benchmarkContext.deviceModel.isNotBlank() &&
            run.benchmarkContext.deviceModel.length <= 80 &&
            run.benchmarkContext.appVersion.isNotBlank() &&
            run.benchmarkContext.appVersion.length <= 80 &&
            run.benchmarkContext.sdkInt in 21..100 &&
            run.benchmarkContext.cpuCores in 1..128 &&
            run.benchmarkContext.workloadIterations in 1_000..1_000_000 &&
            run.sampleNanos.size in 5..12 && run.sampleNanos.all { it > 0L } &&
            run.capturedAtMillis >= 0L

    private fun encode(run: SyntheticBenchmarkRun): JSONObject = JSONObject().apply {
        put("profile", run.profileName)
        put("timestamp", run.capturedAtMillis)
        put("model", run.benchmarkContext.deviceModel)
        put("sdk", run.benchmarkContext.sdkInt)
        put("cores", run.benchmarkContext.cpuCores)
        put("version", run.benchmarkContext.appVersion)
        put("scenario", run.benchmarkContext.scenario.name)
        put("iterations", run.benchmarkContext.workloadIterations)
        put("samples", JSONArray().apply { run.sampleNanos.forEach { put(it) } })
    }

    private fun decode(entry: JSONObject): SyntheticBenchmarkRun? = runCatching {
        val times = entry.optJSONArray("samples") ?: return null
        val samples = buildList {
            repeat(times.length()) { index -> add(times.getLong(index)) }
        }
        val context = SyntheticBenchmarkContext(
            deviceModel = entry.getString("model"),
            sdkInt = entry.getInt("sdk"),
            cpuCores = entry.getInt("cores"),
            appVersion = entry.getString("version"),
            scenario = SyntheticBenchmarkScenario.valueOf(entry.getString("scenario")),
            workloadIterations = entry.getInt("iterations")
        )
        SyntheticBenchmarkRun(
            benchmarkContext = context,
            profileName = entry.getString("profile"),
            sampleNanos = samples,
            capturedAtMillis = entry.getLong("timestamp")
        ).takeIf(::valid)
    }.getOrNull()
}
