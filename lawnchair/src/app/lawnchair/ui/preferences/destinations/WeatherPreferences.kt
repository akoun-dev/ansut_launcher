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

package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.ansut.weather.AnsutWeatherCities
import app.lawnchair.ansut.weather.AnsutWeatherRepository
import app.lawnchair.ansut.weather.AnsutWeatherSync
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/**
 * Page « Météo » des réglages ANSUT : choix de la ville couverte par les
 * widgets météo (liste des villes de Côte d'Ivoire, voir [AnsutWeatherCities])
 * et actualisation manuelle des données Open-Meteo.
 */
@Composable
fun WeatherPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedCity by remember { mutableStateOf(AnsutWeatherRepository.selectedCity(context)) }

    PreferenceLayout(
        label = stringResource(id = R.string.ansut_weather_settings_label),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.ansut_weather_settings_city)) {
            ListPreference(
                entries = AnsutWeatherCities.ALL.map { city ->
                    ListPreferenceEntry(value = city, label = { city.label })
                },
                value = selectedCity,
                onValueChange = { city ->
                    selectedCity = city
                    AnsutWeatherRepository.saveCity(context, city)
                    AnsutWeatherSync.requestRefresh(context)
                },
                label = stringResource(id = R.string.ansut_weather_settings_city),
            )
        }
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(id = R.string.ansut_weather_settings_refresh),
                subtitle = stringResource(id = R.string.ansut_weather_settings_refresh_desc),
                onClick = {
                    AnsutWeatherSync.requestRefresh(context)
                    Toast.makeText(context, R.string.ansut_weather_settings_refreshed, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
