package com.nicky.chargemonitor.session

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SessionExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

class SessionExporter(context: Context) {
    private val exportDirectory = File(context.cacheDir, "session_exports")

    fun export(session: ChargingSession, format: SessionExportFormat): File {
        exportDirectory.mkdirs()
        exportDirectory.listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > EXPORT_MAX_AGE_MILLIS }
            ?.forEach(File::delete)

        val stamp = FILE_STAMP_FORMAT.format(
            Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault()),
        )
        return File(exportDirectory, "charge-session-$stamp.${format.extension}").also { file ->
            val content = when (format) {
                SessionExportFormat.JSON -> session.toJson().toString(2)
                SessionExportFormat.CSV -> session.toCsv()
            }
            file.writeText(content)
        }
    }

    private fun ChargingSession.toJson(): JSONObject {
        val summary = SessionAnalyzer.summarize(this)
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("sessionId", id)
            put("startedAt", startedAtMillis.toIsoTimestamp())
            putNullable("endedAt", endedAtMillis?.toIsoTimestamp())
            put("joinedInProgress", joinedInProgress)
            putNullable("endReason", endReason?.name)
            put("initialSource", initialSource.name)
            put(
                "summary",
                JSONObject().apply {
                    put("durationMillis", summary.durationMillis)
                    put("sampleCount", summary.sampleCount)
                    putNullable("startLevelPercent", summary.startLevelPercent)
                    putNullable("endLevelPercent", summary.endLevelPercent)
                    putNullable("averagePowerWatts", summary.averagePowerWatts)
                    putNullable("medianPowerWatts", summary.medianPowerWatts)
                    putNullable("tenthPercentilePowerWatts", summary.tenthPercentilePowerWatts)
                    putNullable("minimumPowerWatts", summary.minimumPowerWatts)
                    putNullable("maximumPowerWatts", summary.maximumPowerWatts)
                    putNullable("peakTemperatureCelsius", summary.peakTemperatureCelsius)
                    put("thermalLimitedSeconds", summary.thermalLimitedSeconds)
                    putNullable("chargingProfile", summary.chargingProfile?.name)
                    putNullable("sourceCeilingWatts", summary.sourceCeilingWatts)
                    put("diagnosis", summary.diagnosis.title)
                    put("diagnosisDetail", summary.diagnosis.detail)
                },
            )
            put(
                "events",
                JSONArray().apply {
                    events.forEach { event ->
                        put(
                            JSONObject().apply {
                                put("timestamp", event.timestampMillis.toIsoTimestamp())
                                put("elapsedMillis", event.timestampMillis - startedAtMillis)
                                put("type", event.type.name)
                                put("label", event.label)
                            },
                        )
                    }
                },
            )
            put(
                "samples",
                JSONArray().apply {
                    samples.forEach { sample ->
                        put(
                            JSONObject().apply {
                                put("timestamp", sample.timestampMillis.toIsoTimestamp())
                                put("elapsedMillis", sample.elapsedMillis)
                                putNullable("powerMinimumWatts", sample.powerMinimumWatts)
                                putNullable("powerAverageWatts", sample.powerAverageWatts)
                                putNullable("powerMaximumWatts", sample.powerMaximumWatts)
                                putNullable("currentMinimumAmps", sample.currentMinimumAmps)
                                putNullable("currentAverageAmps", sample.currentAverageAmps)
                                putNullable("currentMaximumAmps", sample.currentMaximumAmps)
                                putNullable(
                                    "systemAverageCurrentAmps",
                                    sample.systemAverageCurrentAmps,
                                )
                                putNullable("voltageVolts", sample.voltageVolts)
                                putNullable("levelPercent", sample.levelPercent)
                                putNullable("temperatureCelsius", sample.temperatureCelsius)
                                put("thermalStatus", sample.thermalStatus.name)
                                put("chargeState", sample.chargeState.name)
                                put("powerSource", sample.powerSource.name)
                                putNullable("chargingProfile", sample.chargingProfile?.name)
                                putNullable("sourceCeilingWatts", sample.sourceCeilingWatts)
                                putNullable(
                                    "chargeCounterMilliampHours",
                                    sample.chargeCounterMilliampHours,
                                )
                                putNullable(
                                    "energyCounterWattHours",
                                    sample.energyCounterWattHours,
                                )
                                putNullable(
                                    "chargeTimeRemainingMillis",
                                    sample.chargeTimeRemainingMillis,
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun ChargingSession.toCsv(): String = buildString {
        appendLine(
            listOf(
                "row_type",
                "timestamp",
                "elapsed_ms",
                "event",
                "power_min_w",
                "power_avg_w",
                "power_max_w",
                "current_min_a",
                "current_avg_a",
                "current_max_a",
                "system_current_avg_a",
                "voltage_v",
                "battery_percent",
                "temperature_c",
                "thermal_status",
                "charge_state",
                "power_source",
                "charging_profile",
                "source_ceiling_w",
                "charge_counter_mah",
                "energy_counter_wh",
                "time_to_full_ms",
            ).joinToString(","),
        )
        samples.forEach { sample ->
            appendLine(
                listOf(
                    "sample",
                    sample.timestampMillis.toIsoTimestamp(),
                    sample.elapsedMillis,
                    "",
                    sample.powerMinimumWatts,
                    sample.powerAverageWatts,
                    sample.powerMaximumWatts,
                    sample.currentMinimumAmps,
                    sample.currentAverageAmps,
                    sample.currentMaximumAmps,
                    sample.systemAverageCurrentAmps,
                    sample.voltageVolts,
                    sample.levelPercent,
                    sample.temperatureCelsius,
                    sample.thermalStatus.name,
                    sample.chargeState.name,
                    sample.powerSource.name,
                    sample.chargingProfile?.name,
                    sample.sourceCeilingWatts,
                    sample.chargeCounterMilliampHours,
                    sample.energyCounterWattHours,
                    sample.chargeTimeRemainingMillis,
                ).toCsvRow(),
            )
        }
        events.forEach { event ->
            appendLine(
                listOf(
                    "event",
                    event.timestampMillis.toIsoTimestamp(),
                    event.timestampMillis - startedAtMillis,
                    event.label,
                ).toCsvRow(totalColumns = 22),
            )
        }
    }

    companion object {
        private const val EXPORT_MAX_AGE_MILLIS = 24 * 60 * 60_000L
        private val FILE_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun Long.toIsoTimestamp(): String = Instant.ofEpochMilli(this).toString()

private fun List<Any?>.toCsvRow(totalColumns: Int = size): String =
    List(totalColumns) { index ->
        val value = getOrNull(index)?.toString().orEmpty()
        "\"${value.replace("\"", "\"\"")}\""
    }.joinToString(",")
