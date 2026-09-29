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
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Relevé météo affiché dans les widgets ANSUT (4×1 et 4×2). */
data class AnsutWeatherData(
    val temperature: Double,
    val condition: Int,
    val isDay: Boolean,
    val maxTemp: Double,
    val minTemp: Double,
    val updatedAt: Long,
    /** Dates ISO (yyyy-MM-dd) : aujourd'hui puis les jours suivants. */
    val dailyDates: List<String> = emptyList(),
    /** Maxima quotidiens, même indexation que [dailyDates]. */
    val dailyMax: List<Double> = emptyList(),
    /** Minima quotidiens, même indexation que [dailyDates]. */
    val dailyMin: List<Double> = emptyList(),
    /** Codes WMO quotidiens, même indexation que [dailyDates]. */
    val dailyCodes: List<Int> = emptyList(),
)

/**
 * Source météo du launcher ANSUT, basée sur Open-Meteo (gratuit, sans clé API).
 *
 * - Ville par défaut : Abidjan, Côte d'Ivoire ; modifiable dans les réglages
 *   (Réglages → Météo) ou par surcharge du fichier de préférences "ansut_weather"
 *   (clés "latitude", "longitude", "city_label") pour les déploiements avancés.
 * - Affichage hors-ligne garanti : le dernier relevé valide est mis en cache et
 *   servi immédiatement, la mise à jour réseau n'est qu'un enrichissement.
 * - Les rafraîchissements rapprochés sont dédupliqués (verrou + délai minimal).
 */
object AnsutWeatherRepository {

    private const val PREFS = "ansut_weather"
    private const val KEY_CACHE = "cache_json"

    private const val DEFAULT_LATITUDE = 5.3364
    private const val DEFAULT_LONGITUDE = -4.0267

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    /** Jours demandés à Open-Meteo : aujourd'hui + 3 jours de prévisions. */
    private const val FORECAST_DAYS = 4

    /** Délai minimal entre deux requêtes réseau, toutes causes confondues. */
    private const val MIN_FETCH_INTERVAL_MS = 10_000L

    /** Intervalle minimal entre deux actualisations automatiques (non forcées). */
    private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60_000L

    private val fetchMutex = Mutex()

    fun latitude(context: Context): Double =
        prefs(context).getString("latitude", null)?.toDoubleOrNull() ?: DEFAULT_LATITUDE

    fun longitude(context: Context): Double =
        prefs(context).getString("longitude", null)?.toDoubleOrNull() ?: DEFAULT_LONGITUDE

    fun cityLabel(context: Context): String =
        prefs(context).getString("city_label", null)
            ?: context.getString(R.string.ansut_weather_city)

    /** Ville sélectionnée dans les réglages (retombe sur le défaut si inconnue). */
    fun selectedCity(context: Context): AnsutWeatherCity =
        AnsutWeatherCities.match(cityLabel(context), latitude(context), longitude(context))
            ?: AnsutWeatherCities.DEFAULT

    /** Mémorise la ville choisie dans les réglages puis invalide le rendu. */
    fun saveCity(context: Context, city: AnsutWeatherCity) {
        prefs(context).edit()
            .putString("latitude", city.latitude.toString())
            .putString("longitude", city.longitude.toString())
            .putString("city_label", city.label)
            .apply()
    }

    /** Dernier relevé en cache (peut être null au tout premier lancement). */
    fun getCached(context: Context): AnsutWeatherData? = runCatching {
        val json = prefs(context).getString(KEY_CACHE, null) ?: return null
        fromCacheJson(JSONObject(json))
    }.getOrNull()

    /**
     * Interroge Open-Meteo et met le résultat en cache.
     *
     * @param force true pour bypasser l'intervalle d'actualisation automatique
     *   (appui manuel sur le widget, bouton des réglages). Le délai minimal
     *   anti-tempête [MIN_FETCH_INTERVAL_MS] s'applique toujours.
     * @return le relevé à jour, ou le cache si le réseau/le serveur échoue
     *   (les widgets continuent alors d'afficher le dernier relevé).
     */
    suspend fun refresh(context: Context, force: Boolean = false): AnsutWeatherData? =
        withContext(Dispatchers.IO) {
            fetchMutex.withLock {
                val cached = getCached(context)
                val age = cached?.let { System.currentTimeMillis() - it.updatedAt } ?: Long.MAX_VALUE
                if (cached != null && age < MIN_FETCH_INTERVAL_MS) return@withLock cached
                if (!force && cached != null && age < AUTO_REFRESH_INTERVAL_MS) return@withLock cached
                val url = buildString {
                    append("https://api.open-meteo.com/v1/forecast")
                    append("?latitude=").append(latitude(context))
                    append("&longitude=").append(longitude(context))
                    append("&current=temperature_2m,weather_code,is_day")
                    append("&daily=temperature_2m_max,temperature_2m_min,weather_code")
                    append("&forecast_days=").append(FORECAST_DAYS)
                    append("&timezone=auto")
                }
                val data = fetch(url) ?: return@withLock cached
                prefs(context).edit()
                    .putString(KEY_CACHE, toJson(data).toString())
                    .apply()
                data
            }
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
        val dates = daily.stringList("time").take(FORECAST_DAYS)
        val max = daily.doubleList("temperature_2m_max").take(FORECAST_DAYS)
        val min = daily.doubleList("temperature_2m_min").take(FORECAST_DAYS)
        val codes = daily.intList("weather_code").take(FORECAST_DAYS)
        return AnsutWeatherData(
            temperature = current.getDouble("temperature_2m"),
            condition = current.optInt("weather_code", -1),
            isDay = current.optInt("is_day", 1) == 1,
            maxTemp = max.firstOrNull() ?: current.getDouble("temperature_2m"),
            minTemp = min.firstOrNull() ?: current.getDouble("temperature_2m"),
            updatedAt = System.currentTimeMillis(),
            dailyDates = dates,
            dailyMax = max,
            dailyMin = min,
            dailyCodes = codes,
        )
    }

    private fun toJson(data: AnsutWeatherData): JSONObject = JSONObject().apply {
        put("temperature", data.temperature)
        put("condition", data.condition)
        put("is_day", data.isDay)
        put("max_temp", data.maxTemp)
        put("min_temp", data.minTemp)
        put("updated_at", data.updatedAt)
        put("daily_dates", JSONArray(data.dailyDates))
        put("daily_max", JSONArray(data.dailyMax))
        put("daily_min", JSONArray(data.dailyMin))
        put("daily_codes", JSONArray(data.dailyCodes))
    }

    private fun fromCacheJson(root: JSONObject): AnsutWeatherData {
        val temperature = root.getDouble("temperature")
        val maxTemp = root.optDouble("max_temp", temperature)
        val minTemp = root.optDouble("min_temp", temperature)
        return AnsutWeatherData(
            temperature = temperature,
            condition = root.optInt("condition", -1),
            isDay = root.optBoolean("is_day", true),
            maxTemp = maxTemp,
            minTemp = minTemp,
            updatedAt = root.optLong("updated_at", 0L),
            dailyDates = root.stringList("daily_dates"),
            dailyMax = root.doubleList("daily_max").ifEmpty { listOf(maxTemp) },
            dailyMin = root.doubleList("daily_min").ifEmpty { listOf(minTemp) },
            dailyCodes = root.intList("daily_codes").ifEmpty { listOf(root.optInt("condition", -1)) },
        )
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun JSONObject.doubleList(name: String): List<Double> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { array.optDouble(it) }
    }

    private fun JSONObject.intList(name: String): List<Int> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { array.optInt(it) }
    }

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
