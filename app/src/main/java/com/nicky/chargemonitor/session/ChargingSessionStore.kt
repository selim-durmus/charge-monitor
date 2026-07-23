package com.nicky.chargemonitor.session

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus

class ChargingSessionStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                joined_in_progress INTEGER NOT NULL,
                end_reason TEXT,
                initial_source TEXT NOT NULL,
                accessory_id TEXT,
                accessory_name TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                timestamp INTEGER NOT NULL,
                elapsed INTEGER NOT NULL,
                power_min REAL,
                power_avg REAL,
                power_max REAL,
                current_min REAL,
                current_avg REAL,
                current_max REAL,
                system_current_avg REAL,
                voltage REAL,
                level INTEGER,
                temperature REAL,
                thermal_status TEXT NOT NULL,
                thermal_headroom_30 REAL,
                charge_state TEXT NOT NULL,
                power_source TEXT NOT NULL,
                charging_profile TEXT,
                source_ceiling REAL,
                charge_counter REAL,
                energy_counter REAL,
                time_to_full INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                timestamp INTEGER NOT NULL,
                type TEXT NOT NULL,
                label TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX samples_by_session ON samples(session_id, timestamp)")
        db.execSQL("CREATE INDEX events_by_session ON events(session_id, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE samples ADD COLUMN thermal_headroom_30 REAL")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN accessory_id TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN accessory_name TEXT")
        }
    }

    fun insertSession(session: ChargingSession) {
        writableDatabase.insertOrThrow(
            "sessions",
            null,
            ContentValues().apply {
                put("id", session.id)
                put("started_at", session.startedAtMillis)
                putNullable("ended_at", session.endedAtMillis)
                put("joined_in_progress", if (session.joinedInProgress) 1 else 0)
                putNullable("end_reason", session.endReason?.name)
                put("initial_source", session.initialSource.name)
                putNullable("accessory_id", session.accessoryId)
                putNullable("accessory_name", session.accessoryName)
            },
        )
        trimToLatestSessions()
    }

    fun updateSessionMetadata(session: ChargingSession) {
        writableDatabase.update(
            "sessions",
            ContentValues().apply {
                putNullable("ended_at", session.endedAtMillis)
                putNullable("end_reason", session.endReason?.name)
                putNullable("accessory_id", session.accessoryId)
                putNullable("accessory_name", session.accessoryName)
            },
            "id = ?",
            arrayOf(session.id),
        )
    }

    fun appendSample(sessionId: String, sample: DiagnosticSample) {
        writableDatabase.insertOrThrow(
            "samples",
            null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("timestamp", sample.timestampMillis)
                put("elapsed", sample.elapsedMillis)
                putNullable("power_min", sample.powerMinimumWatts)
                putNullable("power_avg", sample.powerAverageWatts)
                putNullable("power_max", sample.powerMaximumWatts)
                putNullable("current_min", sample.currentMinimumAmps)
                putNullable("current_avg", sample.currentAverageAmps)
                putNullable("current_max", sample.currentMaximumAmps)
                putNullable("system_current_avg", sample.systemAverageCurrentAmps)
                putNullable("voltage", sample.voltageVolts)
                putNullable("level", sample.levelPercent)
                putNullable("temperature", sample.temperatureCelsius)
                put("thermal_status", sample.thermalStatus.name)
                putNullable(
                    "thermal_headroom_30",
                    sample.thermalHeadroomForecast30Seconds,
                )
                put("charge_state", sample.chargeState.name)
                put("power_source", sample.powerSource.name)
                putNullable("charging_profile", sample.chargingProfile?.name)
                putNullable("source_ceiling", sample.sourceCeilingWatts)
                putNullable("charge_counter", sample.chargeCounterMilliampHours)
                putNullable("energy_counter", sample.energyCounterWattHours)
                putNullable("time_to_full", sample.chargeTimeRemainingMillis)
            },
        )
    }

    fun appendEvent(sessionId: String, event: SessionEvent) {
        writableDatabase.insertOrThrow(
            "events",
            null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("timestamp", event.timestampMillis)
                put("type", event.type.name)
                put("label", event.label)
            },
        )
    }

    fun loadLatestSessions(limit: Int = MAX_STORED_SESSIONS): List<ChargingSession> {
        val ids = readableDatabase.query(
            "sessions",
            arrayOf("id"),
            null,
            null,
            null,
            null,
            "started_at DESC",
            limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        return ids.mapNotNull(::loadSession)
    }

    fun loadSession(sessionId: String): ChargingSession? {
        val metadata = readableDatabase.query(
            "sessions",
            SESSION_COLUMNS,
            "id = ?",
            arrayOf(sessionId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            SessionMetadata(
                id = cursor.string("id"),
                startedAtMillis = cursor.long("started_at"),
                endedAtMillis = cursor.nullableLong("ended_at"),
                joinedInProgress = cursor.int("joined_in_progress") != 0,
                endReason = cursor
                    .nullableString("end_reason")
                    ?.enumValueOrNull<SessionEndReason>(),
                initialSource = cursor.string("initial_source").enumValueOrNull<PowerSource>()
                    ?: PowerSource.UNKNOWN,
                accessoryId = cursor.nullableString("accessory_id"),
                accessoryName = cursor.nullableString("accessory_name"),
            )
        }

        val samples = readableDatabase.query(
            "samples",
            SAMPLE_COLUMNS,
            "session_id = ?",
            arrayOf(sessionId),
            null,
            null,
            "timestamp ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DiagnosticSample(
                            timestampMillis = cursor.long("timestamp"),
                            elapsedMillis = cursor.long("elapsed"),
                            powerMinimumWatts = cursor.nullableDouble("power_min"),
                            powerAverageWatts = cursor.nullableDouble("power_avg"),
                            powerMaximumWatts = cursor.nullableDouble("power_max"),
                            currentMinimumAmps = cursor.nullableDouble("current_min"),
                            currentAverageAmps = cursor.nullableDouble("current_avg"),
                            currentMaximumAmps = cursor.nullableDouble("current_max"),
                            systemAverageCurrentAmps = cursor.nullableDouble("system_current_avg"),
                            voltageVolts = cursor.nullableDouble("voltage"),
                            levelPercent = cursor.nullableInt("level"),
                            temperatureCelsius = cursor.nullableDouble("temperature"),
                            thermalStatus = cursor
                                .string("thermal_status")
                                .enumValueOrNull<ThermalStatus>()
                                ?: ThermalStatus.UNKNOWN,
                            thermalHeadroomForecast30Seconds = cursor
                                .nullableDouble("thermal_headroom_30"),
                            chargeState = cursor
                                .string("charge_state")
                                .enumValueOrNull<ChargeState>()
                                ?: ChargeState.UNKNOWN,
                            powerSource = cursor
                                .string("power_source")
                                .enumValueOrNull<PowerSource>()
                                ?: PowerSource.UNKNOWN,
                            chargingProfile = cursor
                                .nullableString("charging_profile")
                                ?.enumValueOrNull<ChargingProfile>(),
                            sourceCeilingWatts = cursor.nullableDouble("source_ceiling"),
                            chargeCounterMilliampHours = cursor.nullableDouble("charge_counter"),
                            energyCounterWattHours = cursor.nullableDouble("energy_counter"),
                            chargeTimeRemainingMillis = cursor.nullableLong("time_to_full"),
                        ),
                    )
                }
            }
        }

        val events = readableDatabase.query(
            "events",
            EVENT_COLUMNS,
            "session_id = ?",
            arrayOf(sessionId),
            null,
            null,
            "timestamp ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SessionEvent(
                            timestampMillis = cursor.long("timestamp"),
                            type = cursor.string("type").enumValueOrNull<SessionEventType>()
                                ?: SessionEventType.CHARGE_STATE_CHANGED,
                            label = cursor.string("label"),
                        ),
                    )
                }
            }
        }

        return ChargingSession(
            id = metadata.id,
            startedAtMillis = metadata.startedAtMillis,
            endedAtMillis = metadata.endedAtMillis,
            joinedInProgress = metadata.joinedInProgress,
            endReason = metadata.endReason,
            initialSource = metadata.initialSource,
            accessoryId = metadata.accessoryId,
            accessoryName = metadata.accessoryName,
            samples = samples,
            events = events,
        )
    }

    private fun trimToLatestSessions() {
        writableDatabase.execSQL(
            """
            DELETE FROM sessions
            WHERE id NOT IN (
                SELECT id FROM sessions ORDER BY started_at DESC LIMIT $MAX_STORED_SESSIONS
            )
            """.trimIndent(),
        )
    }

    private data class SessionMetadata(
        val id: String,
        val startedAtMillis: Long,
        val endedAtMillis: Long?,
        val joinedInProgress: Boolean,
        val endReason: SessionEndReason?,
        val initialSource: PowerSource,
        val accessoryId: String?,
        val accessoryName: String?,
    )

    companion object {
        private const val DATABASE_NAME = "charging_sessions.db"
        private const val DATABASE_VERSION = 3

        private val SESSION_COLUMNS = arrayOf(
            "id",
            "started_at",
            "ended_at",
            "joined_in_progress",
            "end_reason",
            "initial_source",
            "accessory_id",
            "accessory_name",
        )
        private val SAMPLE_COLUMNS = arrayOf(
            "timestamp",
            "elapsed",
            "power_min",
            "power_avg",
            "power_max",
            "current_min",
            "current_avg",
            "current_max",
            "system_current_avg",
            "voltage",
            "level",
            "temperature",
            "thermal_status",
            "thermal_headroom_30",
            "charge_state",
            "power_source",
            "charging_profile",
            "source_ceiling",
            "charge_counter",
            "energy_counter",
            "time_to_full",
        )
        private val EVENT_COLUMNS = arrayOf("timestamp", "type", "label")
    }
}

private fun ContentValues.putNullable(key: String, value: String?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Long?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Int?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Double?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string(name: String): String = getString(column(name))
private fun Cursor.nullableString(name: String): String? =
    column(name).let { if (isNull(it)) null else getString(it) }

private fun Cursor.long(name: String): Long = getLong(column(name))
private fun Cursor.nullableLong(name: String): Long? =
    column(name).let { if (isNull(it)) null else getLong(it) }

private fun Cursor.int(name: String): Int = getInt(column(name))
private fun Cursor.nullableInt(name: String): Int? =
    column(name).let { if (isNull(it)) null else getInt(it) }

private fun Cursor.nullableDouble(name: String): Double? =
    column(name).let { if (isNull(it)) null else getDouble(it) }

private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }
