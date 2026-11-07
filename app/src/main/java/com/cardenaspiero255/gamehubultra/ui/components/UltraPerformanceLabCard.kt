package com.cardenaspiero255.gamehubultra.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.BuildConfig
import com.cardenaspiero255.gamehubultra.data.SyntheticBenchmarkStore
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkContext
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkRunner
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkScenario
import com.cardenaspiero255.gamehubultra.domain.SyntheticComparisonStatus
import com.cardenaspiero255.gamehubultra.domain.UltraBenchmarkStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * CAR-62. This runs a bounded CPU-only synthetic benchmark off the main thread,
 * stores the exact scenario context, and does not make FPS or gaming claims.
 */
@Composable
internal fun UltraPerformanceLabCard(
    selectedProfileName: String,
    runnerFactory: () -> SyntheticBenchmarkRunner = { SyntheticBenchmarkRunner() }
) {
    val context = LocalContext.current
    val store = remember(context) { SyntheticBenchmarkStore(context) }
    val scope = rememberCoroutineScope()
    var history by remember(store) { mutableStateOf(store.readAll()) }
    var running by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val last = history.lastOrNull()
    val quality = last?.let(UltraBenchmarkStatistics::quality)
    val medianNanos = last?.let(UltraBenchmarkStatistics::medianNanos)
    val baseline = last?.let { latest ->
        history.dropLast(1).lastOrNull { previous ->
            previous.benchmarkContext == latest.benchmarkContext &&
                previous.profileName != latest.profileName
        }
    }
    val comparison = if (last != null && baseline != null) {
        UltraBenchmarkStatistics.compare(baseline, last)
    } else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ULTRA PERFORMANCE LAB", style = MaterialTheme.typography.titleMedium)
            Text("Prueba sintética: no mide FPS de juegos.", style = MaterialTheme.typography.bodySmall)
            Text("Escenario CPU_INTEGER_V1 · 250000 iteraciones · 7 muestras.")
            Text("El perfil es una etiqueta; no demuestra que Android haya aplicado un cambio.")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !running, onClick = {
                    if (!running) {
                        running = true
                        failed = false
                        scope.launch {
                            try {
                                val benchmarkContext = SyntheticBenchmarkContext(
                                    deviceModel = Build.MODEL?.trim()?.take(80)
                                        ?.ifBlank { "Android" } ?: "Android",
                                    sdkInt = Build.VERSION.SDK_INT,
                                    cpuCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 128),
                                    appVersion = BuildConfig.VERSION_NAME,
                                    scenario = SyntheticBenchmarkScenario.CPU_INTEGER_V1,
                                    workloadIterations = 250_000
                                )
                                val result = withContext(Dispatchers.Default) {
                                    runnerFactory().measure(benchmarkContext, selectedProfileName)
                                }
                                withContext(Dispatchers.IO) { store.append(result) }
                                history = store.readAll()
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                running = false
                            }
                        }
                    }
                }) { Text(if (running) "MIDIENDO" else "EJECUTAR PRUEBA") }
                TextButton(enabled = !running && history.isNotEmpty(), onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.clear() }
                            history = emptyList()
                            failed = false
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                }) { Text("LIMPIAR RESULTADOS") }
            }
            if (last == null) {
                Text("Sin mediciones locales todavía.")
            } else {
                Text("Última medición: ${last.profileName} · ${last.sampleNanos.size} muestras")
                Text("Mediana: ${medianNanos ?: "n/d"} ns · calidad ${quality?.name ?: "n/d"}")
                Text("Contexto: Android ${last.benchmarkContext.sdkInt}, " +
                    "${last.benchmarkContext.cpuCores} núcleos, app ${last.benchmarkContext.appVersion}")
                Text("Historial local: ${history.size}/12 mediciones.")
            }
            val comparisonText = when (comparison?.status) {
                SyntheticComparisonStatus.FASTER ->
                    "Carga sintética más rápida: " + String.format(Locale.US, "%.1f", comparison.relativePercent) + "%; no implica mejora de FPS."
                SyntheticComparisonStatus.SLOWER ->
                    "Carga sintética más lenta: " + String.format(Locale.US, "%.1f", -requireNotNull(comparison.relativePercent)) + "%; no implica pérdida de FPS."
                SyntheticComparisonStatus.INCOMPARABLE ->
                    "Comparación real: contextos incompatibles, sin conclusión."
                else -> "Comparación real: sin datos suficientes."
            }
            Text(comparisonText, style = MaterialTheme.typography.bodySmall)
            if (failed) {
                Text("La medición o el guardado falló; no se atribuyen resultados.")
            }
        }
    }
}
