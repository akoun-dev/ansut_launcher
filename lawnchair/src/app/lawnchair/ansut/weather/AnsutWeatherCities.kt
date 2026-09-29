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

import kotlin.math.abs

/** Ville couverte par les widgets météo ANSUT. */
data class AnsutWeatherCity(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Villes de Côte d'Ivoire proposées dans les réglages du launcher
 * (Réglages → Météo → Ville). Coordonnées de centres-ville approximatives,
 * largement suffisantes pour une prévision météo. Le défaut est Abidjan.
 */
object AnsutWeatherCities {

    val DEFAULT = AnsutWeatherCity("Abidjan", 5.3364, -4.0267)

    val ALL = listOf(
        DEFAULT,
        AnsutWeatherCity("Yamoussoukro", 6.8276, -5.2893),
        AnsutWeatherCity("Bouaké", 7.6906, -5.0300),
        AnsutWeatherCity("Daloa", 6.8776, -6.4502),
        AnsutWeatherCity("San-Pédro", 4.7485, -6.6363),
        AnsutWeatherCity("Korhogo", 9.4580, -5.6297),
        AnsutWeatherCity("Man", 7.4125, -7.5539),
        AnsutWeatherCity("Gagnoa", 6.1319, -5.9506),
        AnsutWeatherCity("Abengourou", 6.7297, -3.4964),
        AnsutWeatherCity("Bondoukou", 8.0402, -2.8000),
        AnsutWeatherCity("Odienné", 9.5051, -7.5643),
        AnsutWeatherCity("Divo", 5.8374, -5.3563),
    )

    /**
     * Retrouve la ville connue correspondant aux préférences stockées, par
     * libellé puis par coordonnées (déploiements surchargés sans "city_label").
     */
    fun match(label: String, latitude: Double, longitude: Double): AnsutWeatherCity? =
        ALL.firstOrNull { it.label == label }
            ?: ALL.firstOrNull {
                abs(it.latitude - latitude) < 0.05 && abs(it.longitude - longitude) < 0.05
            }
}
