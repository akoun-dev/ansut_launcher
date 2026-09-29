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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Coordination des widgets météo ANSUT (4×1 et 4×2) : action de rafraîchissement
 * partagée (appui sur un widget, bouton des réglages) et re-rendu des deux
 * familles de widgets après chaque mise à jour des données, pour qu'elles
 * restent toujours synchronisées.
 */
object AnsutWeatherSync {

    const val ACTION_REFRESH = "app.lawnchair.ansut.weather.ACTION_REFRESH"

    /** Demande l'actualisation des widgets météo (données + rendu). */
    fun requestRefresh(context: Context) {
        listOf(
            AnsutWeatherWidgetProvider::class.java,
            AnsutWeatherWidgetProvider4x2::class.java,
        ).forEach { providerClass ->
            context.sendBroadcast(Intent(context, providerClass).setAction(ACTION_REFRESH))
        }
    }

    /** Re-rend les widgets 4×1 et 4×2 depuis le cache (aucun réseau). */
    internal fun renderAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        AnsutWeatherWidgetProvider.render(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, AnsutWeatherWidgetProvider::class.java)),
        )
        AnsutWeatherWidgetProvider4x2.render(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, AnsutWeatherWidgetProvider4x2::class.java)),
        )
    }
}
