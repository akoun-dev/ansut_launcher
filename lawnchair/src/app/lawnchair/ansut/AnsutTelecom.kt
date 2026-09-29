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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.android.launcher3.R

/**
 * Centralisation des raccourcis télécom de Côte d'Ivoire utilisés par les widgets ANSUT.
 *
 * Les codes USSD vivent dans les ressources (`ansut_ussd_*`, non traduisibles) afin de
 * rester synchronisés avec les libellés affichés dans les widgets. Si un opérateur
 * change son code, une seule ligne de `strings.xml` est à mettre à jour.
 */
object AnsutTelecom {

    // Packages des applications mobile money (dossier « Services » du workspace + widget).
    const val PACKAGE_WAVE = "com.wave.personal"
    const val PACKAGE_ORANGE_MONEY = "com.orange.orangemoneyafrique"
    const val PACKAGE_MOOV_MONEY = "ci.moovmoney.mmpayapi"

    // Numéro d'urgence universel, composé via ACTION_DIAL (aucune permission requise).
    const val EMERGENCY_NUMBER = "112"

    const val ANSUT_WEBSITE = "https://www.ansut.ci"

    fun orangeUssd(context: Context): String = context.getString(R.string.ansut_ussd_orange)

    fun mtnUssd(context: Context): String = context.getString(R.string.ansut_ussd_mtn)

    fun moovUssd(context: Context): String = context.getString(R.string.ansut_ussd_moov)

    /**
     * Ouvre le composeur pré-rempli avec le code (USSD ou numéro).
     * [Uri.fromParts] encode les `#` en `%23` : le composeur reçoit bien `#144#`.
     * On utilise ACTION_DIAL (et non ACTION_CALL) pour éviter toute permission
     * et tout appel accidentel — l'utilisateur valide l'appel lui-même.
     */
    fun dialIntent(numberOrCode: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numberOrCode, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Panneau système de réglage des données mobiles (API 29+),
     * ou paramètres réseau sans fil sur les versions plus anciennes.
     */
    fun dataSettingsIntent(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
