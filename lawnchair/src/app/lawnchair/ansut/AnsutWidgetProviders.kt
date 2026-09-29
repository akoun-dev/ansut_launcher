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

package app.lawnchair.ansut

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Widget « Solde & Forfaits » (4×1) : vérifier son solde Orange, MTN ou Moov
 * en un appui, et ouvrir les réglages des données mobiles.
 *
 * Chaque bouton ouvre le composeur pré-rempli avec le code USSD
 * (aucune permission requise, l'utilisateur valide l'appel).
 */
class AnsutSoldeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.ansut_solde_widget)
        AnsutWidgetBindings.bindBalanceRow(context, views)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}

/**
 * Widget « Services ANSUT » (4×2) : solde des trois opérateurs, réglages
 * données, applications mobile money (Wave, Orange Money, Moov Money) et
 * appel d'urgence. Les applications absentes ouvrent leur fiche Play Store.
 */
class AnsutServicesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.ansut_services_widget)
        AnsutWidgetBindings.bindBalanceRow(context, views)
        AnsutWidgetBindings.bindServiceRow(context, views)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}

/** Câblage des PendingIntents des deux widgets ANSUT. */
private object AnsutWidgetBindings {

    private const val REQUEST_ORANGE = 1001
    private const val REQUEST_MTN = 1002
    private const val REQUEST_MOOV = 1003
    private const val REQUEST_DATA = 1004
    private const val REQUEST_WAVE = 1005
    private const val REQUEST_ORANGE_MONEY = 1006
    private const val REQUEST_MOOV_MONEY = 1007
    private const val REQUEST_EMERGENCY = 1008

    fun bindBalanceRow(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(
            R.id.ansut_btn_orange,
            dialPendingIntent(context, AnsutTelecom.orangeUssd(context), REQUEST_ORANGE),
        )
        views.setOnClickPendingIntent(
            R.id.ansut_btn_mtn,
            dialPendingIntent(context, AnsutTelecom.mtnUssd(context), REQUEST_MTN),
        )
        views.setOnClickPendingIntent(
            R.id.ansut_btn_moov,
            dialPendingIntent(context, AnsutTelecom.moovUssd(context), REQUEST_MOOV),
        )
        views.setOnClickPendingIntent(
            R.id.ansut_btn_data,
            activityPendingIntent(context, AnsutTelecom.dataSettingsIntent(), REQUEST_DATA),
        )
    }

    fun bindServiceRow(context: Context, views: RemoteViews) {
        bindAppButton(context, views, R.id.ansut_btn_wave, AnsutTelecom.PACKAGE_WAVE, REQUEST_WAVE)
        bindAppButton(context, views, R.id.ansut_btn_orange_money, AnsutTelecom.PACKAGE_ORANGE_MONEY, REQUEST_ORANGE_MONEY)
        bindAppButton(context, views, R.id.ansut_btn_moov_money, AnsutTelecom.PACKAGE_MOOV_MONEY, REQUEST_MOOV_MONEY)
        views.setOnClickPendingIntent(
            R.id.ansut_btn_emergency,
            dialPendingIntent(context, AnsutTelecom.EMERGENCY_NUMBER, REQUEST_EMERGENCY),
        )
    }

    private fun dialPendingIntent(context: Context, code: String, requestCode: Int): PendingIntent =
        activityPendingIntent(context, AnsutTelecom.dialIntent(code), requestCode)

    private fun activityPendingIntent(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Lance l'application si elle est installée, sinon ouvre sa fiche Play Store. */
    private fun bindAppButton(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        packageName: String,
        requestCode: Int,
    ) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(viewId, activityPendingIntent(context, launchIntent, requestCode))
    }
}
