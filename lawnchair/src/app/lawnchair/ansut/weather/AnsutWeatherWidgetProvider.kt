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
import android.widget.RemoteViews
import com.android.launcher3.R
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget « Météo » (4×1), style One UI : fond verre arrondi, grande température,
 * icône du temps et ville. Données Open-Meteo, cache hors-ligne.
 *
 * Un appui n'importe où sur le widget déclenche une actualisation manuelle ;
 * après chaque mise à jour, les widgets 4×1 ET 4×2 sont re-rendus
 * (voir [AnsutWeatherSync.renderAll]).
 */
class AnsutWeatherWidgetProvider : AppWidgetProvider() {

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
            val ids = manager.getAppWidgetIds(ComponentName(context, AnsutWeatherWidgetProvider::class.java))
            render(context, manager, ids)
            refreshAsync(context, force = true)
        }
    }

    /**
     * Rendu immédiat depuis le cache (offline-first), puis enrichissement réseau.
     * [goAsync] garde le processus actif le temps de la requête (≈ 10 s max).
     */
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
        private const val TAG = "AnsutWeatherWidget"
        private const val REQUEST_REFRESH = 2001

        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun render(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.ansut_weather_widget)
            val data = AnsutWeatherRepository.getCached(context)
            if (data == null) {
                views.setTextViewText(R.id.ansut_weather_temp, "–°")
                views.setTextViewText(R.id.ansut_weather_condition, context.getString(R.string.ansut_weather_unavailable))
                views.setImageViewResource(R.id.ansut_weather_icon, R.drawable.ic_ansut_w_cloud)
            } else {
                views.setTextViewText(R.id.ansut_weather_temp, formatTemperature(data.temperature))
                views.setTextViewText(R.id.ansut_weather_condition, AnsutWeatherRepository.labelFor(context, data.condition))
                views.setImageViewResource(R.id.ansut_weather_icon, AnsutWeatherRepository.iconFor(data.condition, data.isDay))
            }
            views.setTextViewText(R.id.ansut_weather_city, AnsutWeatherRepository.cityLabel(context))
            views.setOnClickPendingIntent(R.id.ansut_weather_root, refreshPendingIntent(context))
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        }

        private fun formatTemperature(temperature: Double): String =
            String.format(Locale.getDefault(), "%.0f°", temperature)

        private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            Intent(context, AnsutWeatherWidgetProvider::class.java).setAction(AnsutWeatherSync.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
