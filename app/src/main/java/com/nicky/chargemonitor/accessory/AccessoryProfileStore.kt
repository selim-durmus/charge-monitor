package com.nicky.chargemonitor.accessory

import android.content.Context
import androidx.core.content.edit
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.PowerSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AccessoryProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun profiles(): List<AccessoryProfile> = loadProfiles()

    @Synchronized
    fun profileFor(reading: BatteryReading): AccessoryProfile? {
        val signature = reading.accessorySignature() ?: return null
        return loadProfiles().firstOrNull { it.signature == signature }
    }

    @Synchronized
    fun saveName(
        reading: BatteryReading,
        name: String,
        timestampMillis: Long = System.currentTimeMillis(),
    ): AccessoryProfile {
        val signature = requireNotNull(reading.accessorySignature()) {
            "A connected power source is required"
        }
        val cleanName = name.trim().take(MAX_NAME_LENGTH)
        require(cleanName.isNotEmpty()) { "Accessory name cannot be empty" }

        val profiles = loadProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst { it.signature == signature }
        val profile = if (existingIndex >= 0) {
            profiles[existingIndex].copy(
                name = cleanName,
                powerSource = reading.powerSource,
                updatedAtMillis = timestampMillis,
            ).also { profiles[existingIndex] = it }
        } else {
            AccessoryProfile(
                id = UUID.randomUUID().toString(),
                signature = signature,
                name = cleanName,
                powerSource = reading.powerSource,
                createdAtMillis = timestampMillis,
                updatedAtMillis = timestampMillis,
            ).also(profiles::add)
        }
        persist(profiles)
        return profile
    }

    private fun loadProfiles(): List<AccessoryProfile> {
        val raw = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        AccessoryProfile(
                            id = item.getString("id"),
                            signature = item.getString("signature"),
                            name = item.getString("name"),
                            powerSource = enumValues<PowerSource>().firstOrNull {
                                it.name == item.optString("powerSource")
                            } ?: PowerSource.UNKNOWN,
                            createdAtMillis = item.optLong("createdAtMillis"),
                            updatedAtMillis = item.optLong("updatedAtMillis"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(profiles: List<AccessoryProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("signature", profile.signature)
                    put("name", profile.name)
                    put("powerSource", profile.powerSource.name)
                    put("createdAtMillis", profile.createdAtMillis)
                    put("updatedAtMillis", profile.updatedAtMillis)
                },
            )
        }
        preferences.edit {
            putString(KEY_PROFILES, array.toString())
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "accessory_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val MAX_NAME_LENGTH = 40
    }
}
