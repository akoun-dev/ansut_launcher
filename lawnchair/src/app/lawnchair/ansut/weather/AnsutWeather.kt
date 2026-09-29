/*
 * Copyright 2025, ANSUT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ansut.weather

import android.content.Context
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Relevé météo affiché dans le widget ANSUT. */
data class AnsutWeatherData(
    val temperature: Double,
    val condition: Int,
    val isDay: Boolean,
    val maxTemp: Double,
    val minTemp: Double,
    val updatedAt: Long,
)

/**
 * Source météo du launcher ANSUT, basée sur Open-Meteo (gratuit, sans clé API).
 *
 * - Coordonnées par défaut : Abidjan, Côte d'Ivoire.
 * - Déployeurs avancés : surcharger via le fichier de préférences "ansut_weather"
 *   (clés "latitude", "longitude", "city_label") avant la première installation.
 * - Affichage hors-ligne garanti : le dernier relevé valide est mis en cache et
 *   servi immédiatement, la mise à jour réseau n'est qu'un enrichissement.
 */
object AnsutWeatherRepository {

    private const val PREFS = "ansut_weather"
    private const val KEY_CACHE = "cache_json"

    private const val DEFAULT_LATITUDE = 5.3364
    private const val DEFAULT_LONGITUDE = -4.0267

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    fun latitude(context: Context): Double =
        prefs(context).getString("latitude", null)?.toDoubleOrNull() ?: DEFAULT_LATITUDE

    fun longitude(context: Context): Double =
        prefs(context).getString("longitude", null)?.toDoubleOrNull() ?: DEFAULT_LONGITUDE

    fun cityLabel(context: Context): String =
        prefs(context).getString("city_label", null)
            ?: context.getString(R.string.ansut_weather_city)

    /** Dernier relevé en cache (peut être null au tout premier lancement). */
    fun getCached(context: Context): AnsutWeatherData? = runCatching {
        val json = prefs(context).getString(KEY_CACHE, null) ?: return null
        fromJson(JSONObject(json))
    }.getOrNull()

    /**
     * Interroge Open-Meteo et met le résultat en cache.
     * Retourne le relevé à jour, ou null si le réseau/le serveur échoue
     * (le widget continue alors d'afficher le cache).
     */
    suspend fun refresh(context: Context): AnsutWeatherData? = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(latitude(context))
            append("&longitude=").append(longitude(context))
            append("&current=temperature_2m,weather_code,is_day")
            append("&daily=temperature_2m_max,temperature_2m_min")
            append("&forecast_days=1&timezone=auto")
        }
        val data = fetch(url) ?: return@withContext null
        prefs(context).edit()
            .putString(KEY_CACHE, toJson(data).toString())
            .apply()
        data
    }

    private fun fetch(url: String): AnsutWeatherData? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "ANSUT-Launcher-Weather/1.0")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            fromJson(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun fromJson(root: JSONObject): AnsutWeatherData {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        return AnsutWeatherData(
            temperature = current.getDouble("temperature_2m"),
            condition = current.optInt("weather_code", -1),
            isDay = current.optInt("is_day", 1) == 1,
            maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0),
            minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun toJson(data: AnsutWeatherData): JSONObject = JSONObject().apply {
        put("temperature", data.temperature)
        put("condition", data.condition)
        put("is_day", data.isDay)
        put("max_temp", data.maxTemp)
        put("min_temp", data.minTemp)
        put("updated_at", data.updatedAt)
    }

    private fun fromCacheJson(root: JSONObject): AnsutWeatherData = AnsutWeatherData(
        temperature = root.getDouble("temperature"),
        condition = root.optInt("condition", -1),
        isDay = root.optBoolean("is_day", true),
        maxTemp = root.optDouble("max_temp", root.getDouble("temperature")),
        minTemp = root.optDouble("min_temp", root.getDouble("temperature")),
        updatedAt = root.optLong("updated_at", 0L),
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Icône vectorielle blanche correspondant au code WMO d'Open-Meteo. */
    fun iconFor(condition: Int, isDay: Boolean): Int = when (condition) {
        0 -> if (isDay) R.drawable.ic_ansut_w_sunny else R.drawable.ic_ansut_w_night
        1, 2 -> if (isDay) R.drawable.ic_ansut_w_partly else R.drawable.ic_ansut_w_night
        3 -> R.drawable.ic_ansut_w_cloud
        45, 48 -> R.drawable.ic_ansut_w_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_ansut_w_rain
        61, 63, 65, 66, 67 -> R.drawable.ic_ansut_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_ansut_w_snow
        80, 81, 82 -> R.drawable.ic_ansut_w_showers
        95, 96, 99 -> R.drawable.ic_ansut_w_thunder
        else -> R.drawable.ic_ansut_w_cloud
    }

    /** Libellé français/anglais du temps qu'il fait (codes WMO). */
    fun labelFor(context: Context, condition: Int): String = context.getString(
        when (condition) {
            0 -> R.string.ansut_weather_cond_clear
            1 -> R.string.ansut_weather_cond_mostly_clear
            2 -> R.string.ansut_weather_cond_partly
            3 -> R.string.ansut_weather_cond_overcast
            45, 48 -> R.string.ansut_weather_cond_fog
            51, 53, 55, 56, 57 -> R.string.ansut_weather_cond_drizzle
            61, 63, 65, 66, 67 -> R.string.ansut_weather_cond_rain
            71, 73, 75, 77, 85, 86 -> R.string.ansut_weather_cond_snow
            80, 81, 82 -> R.string.ansut_weather_cond_showers
            95, 96, 99 -> R.string.ansut_weather_cond_thunder
            else -> R.string.ansut_weather_cond_overcast
        },
    )
}
