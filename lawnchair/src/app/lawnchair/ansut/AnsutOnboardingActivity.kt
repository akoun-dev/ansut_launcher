package app.lawnchair.ansut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.R

/**
 * ANSUT : écran de bienvenue au premier démarrage.
 *
 * Affiché une seule fois, par-dessus le bureau, tant que l'utilisateur n'est
 * pas passé par l'écran (bouton « Commencer », retour ou accueil). Il propose
 * de définir ANSUT comme launcher par défaut (réglages système) et présente
 * l'expérience ANSUT déjà pré-configurée (apps, widgets Solde/Météo/Services).
 */
class AnsutOnboardingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ansut_onboarding_activity)

        findViewById<View>(R.id.ansut_onboarding_step1_cta).setOnClickListener {
            // Réglages système « Application d'accueil »
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        findViewById<View>(R.id.ansut_onboarding_cta).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        updateDefaultLauncherState()
    }

    override fun onPause() {
        super.onPause()
        // Une seule visite : quitter l'écran (Continuer, retour ou accueil)
        // marque l'onboarding comme vu — on ne re-sollicite jamais l'utilisateur.
        setDone(this)
    }

    private fun updateDefaultLauncherState() {
        val isDefault = isDefaultLauncher()
        findViewById<View>(R.id.ansut_onboarding_step1_cta).visibility =
            if (isDefault) View.GONE else View.VISIBLE
        findViewById<View>(R.id.ansut_onboarding_step1_done).visibility =
            if (isDefault) View.VISIBLE else View.GONE
    }

    companion object {
        private const val PREF_KEY = "onboarding_done"

        fun isDone(context: Context): Boolean = context
            .getSharedPreferences("ansut_branding", Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)

        private fun setDone(context: Context) {
            context.getSharedPreferences("ansut_branding", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY, true)
                .apply()
        }
    }
}
