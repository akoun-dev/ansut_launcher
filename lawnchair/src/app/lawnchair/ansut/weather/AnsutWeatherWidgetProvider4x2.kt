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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget « Météo détaillée » (4×2), style One UI : température et condition du
 * jour, max/min du jour et prévisions sur 3 jours (libellé, icône, max/min),
 * dans la même carte verre que les autres widgets ANSUT.
 *
 * Un appui n'importe où sur le widget déclenche une actualisation manuelle ;
 * après chaque mise à jour, les widgets 4×1 ET 4×2 sont re-rendus
 * (voir [AnsutWeatherSync.renderAll]).
 */
class AnsutWeatherWidgetProvider4x2 : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetManager, appWidgetIds)
        refreshAsync(context, force = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AnsutWeatherSync.ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AnsutWeatherWidgetProvider4x2::class.java))
            render(context, manager, ids)
            refreshAsync(context, force = true)
        }
    }

    private fun refreshAsync(context: Context, force: Boolean) {
        val pendingResult = goAsync()
        refreshScope.launch {
            try {
                AnsutWeatherRepository.refresh(context, force)
                AnsutWeatherSync.renderAll(context)
            } catch (t: Throwable) {
                Log.w(TAG, "Weather refresh failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AnsutWeatherWidget4x2"
        private const val REQUEST_REFRESH = 2002

        /** Nombre de colonnes de prévisions affichées (aujourd'hui + 2 jours). */
        private const val FORECAST_DAYS = 3

        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun render(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.ansut_weather_widget_4x2)
            val data = AnsutWeatherRepository.getCached(context)
            if (data == null) {
                views.setTextViewText(R.id.ansut_weather_temp, "–°")
                views.setTextViewText(R.id.ansut_weather_condition, context.getString(R.string.ansut_weather_unavailable))
                views.setImageViewResource(R.id.ansut_weather_icon, R.drawable.ic_ansut_w_cloud)
                views.setTextViewText(R.id.ansut_weather_city, AnsutWeatherRepository.cityLabel(context))
                views.setTextViewText(R.id.ansut_weather_minmax, "")
                views.setViewVisibility(R.id.ansut_weather_forecast_row, View.GONE)
            } else {
                views.setTextViewText(R.id.ansut_weather_temp, formatTemperature(data.temperature))
                views.setTextViewText(R.id.ansut_weather_condition, AnsutWeatherRepository.labelFor(context, data.condition))
                views.setImageViewResource(R.id.ansut_weather_icon, AnsutWeatherRepository.iconFor(data.condition, data.isDay))
                views.setTextViewText(R.id.ansut_weather_city, AnsutWeatherRepository.cityLabel(context))
                views.setTextViewText(
                    R.id.ansut_weather_minmax,
                    context.getString(
                        R.string.ansut_weather_minmax_fmt,
                        formatTemperature(data.maxTemp),
                        formatTemperature(data.minTemp),
                    ),
                )
                val forecastReady = data.dailyDates.size >= FORECAST_DAYS &&
                    data.dailyMax.size >= FORECAST_DAYS &&
                    data.dailyMin.size >= FORECAST_DAYS &&
                    data.dailyCodes.size >= FORECAST_DAYS
                if (forecastReady) {
                    views.setViewVisibility(R.id.ansut_weather_forecast_row, View.VISIBLE)
                    for (index in 0 until FORECAST_DAYS) {
                        bindForecastDay(views, context, index, data)
                    }
                } else {
                    // Cache ancien (sans prévisions) : la ligne du jour reste seule.
                    views.setViewVisibility(R.id.ansut_weather_forecast_row, View.GONE)
                }
            }
            views.setOnClickPendingIntent(R.id.ansut_weather_root, refreshPendingIntent(context))
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        }

        private fun bindForecastDay(views: RemoteViews, context: Context, index: Int, data: AnsutWeatherData) {
            // NB : pas d'arithmétique sur les R.id (aapt2 ne garantit pas leur contiguïté).
            val labelId = when (index) {
                0 -> R.id.ansut_weather_day0_label
                1 -> R.id.ansut_weather_day1_label
                else -> R.id.ansut_weather_day2_label
            }
            val iconId = when (index) {
                0 -> R.id.ansut_weather_day0_icon
                1 -> R.id.ansut_weather_day1_icon
                else -> R.id.ansut_weather_day2_icon
            }
            val maxId = when (index) {
                0 -> R.id.ansut_weather_day0_max
                1 -> R.id.ansut_weather_day1_max
                else -> R.id.ansut_weather_day2_max
            }
            val minId = when (index) {
                0 -> R.id.ansut_weather_day0_min
                1 -> R.id.ansut_weather_day1_min
                else -> R.id.ansut_weather_day2_min
            }
            views.setTextViewText(labelId, dayLabel(context, data.dailyDates[index], index))
            views.setImageViewResource(iconId, AnsutWeatherRepository.iconFor(data.dailyCodes[index], isDay = true))
            views.setTextViewText(maxId, formatTemperature(data.dailyMax[index]))
            views.setTextViewText(minId, formatTemperature(data.dailyMin[index]))
        }

        /** « Aujourd'hui », « Demain », puis le jour localisé (ex. « Lun. »). */
        private fun dayLabel(context: Context, isoDate: String, index: Int): String = when (index) {
            0 -> context.getString(R.string.ansut_weather_day_today)
            1 -> context.getString(R.string.ansut_weather_day_tomorrow)
            else ->
                runCatching {
                    LocalDate.parse(isoDate).dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .replaceFirstChar { it.uppercase(Locale.getDefault()) }
                }.getOrDefault("")
        }

        private fun formatTemperature(temperature: Double): String =
            String.format(Locale.getDefault(), "%.0f°", temperature)

        private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            Intent(context, AnsutWeatherWidgetProvider4x2::class.java).setAction(AnsutWeatherSync.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
