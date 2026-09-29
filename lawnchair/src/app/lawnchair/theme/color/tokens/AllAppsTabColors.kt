package app.lawnchair.theme.color.tokens

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.luminance
import app.lawnchair.ansut.AnsutTheme
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.UiColorMode
import app.lawnchair.util.getAllAppsBaseColor
import dev.kdrag0n.monet.theme.ColorScheme

/** Shared colors for Personal/Work tabs in the app drawer (ANSUT One UI dark glass). */
object AllAppsTabColors {

    /** Effective all-apps background (custom color or One UI scrim default, fully opaque). */
    private fun drawerBackgroundColor(context: Context): Int {
        return getAllAppsBaseColor(context, ColorTokens.AllAppsScrimColor.resolveColor(context))
    }

    fun selectedBackground(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val prefs2 = PreferenceManager2.getInstance(context)
        val customColor = prefs2.workProfileTabBackgroundColor.firstCached()
            .colorPreferenceEntry.lightColor.invoke(context)
        return if (customColor != 0) {
            customColor
        } else {
            // ANSUT One UI: translucent white glass pill, like the drawer search bar.
            StaticColorToken(AnsutTheme.PILL).resolveColor(context)
        }
    }

    /**
     * Text color for the selected tab, chosen for contrast against the pill
     * composited over the drawer background: a translucent glass pill on the
     * dark drawer must pick light text, not the (nearly white) color of its
     * own low-alpha fill.
     */
    fun selectedText(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val background = selectedBackground(context, scheme, uiColorMode)
        val effective = ColorUtils.compositeColors(background, drawerBackgroundColor(context))
        return if (effective.luminance > 0.5f) {
            ColorTokens.Neutral1_900.resolveColor(context, scheme, uiColorMode)
        } else {
            ColorTokens.Neutral1_50.resolveColor(context, scheme, uiColorMode)
        }
    }

    /**
     * Text color for unselected tabs. Matches the unselected pill (Surface tint,
     * see DrawableTokens.AllAppsTabsBackground) composited over the drawer
     * background: One UI light glass text (#B3FFFFFF) on the dark drawer, dark
     * theme secondary color when the pill reads as light (e.g. light theme).
     */
    fun unselectedText(
        context: Context,
        scheme: ColorScheme,
        uiColorMode: UiColorMode,
    ): Int {
        val pill = ColorTokens.Surface.resolveColor(context, scheme, uiColorMode)
        val effective = ColorUtils.compositeColors(pill, drawerBackgroundColor(context))
        return if (effective.luminance > 0.5f) {
            ColorTokens.TextColorSecondary.resolveColor(context, scheme, uiColorMode)
        } else {
            StaticColorToken(AnsutTheme.TEXT_SECONDARY).resolveColor(context)
        }
    }
}
