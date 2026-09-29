package app.lawnchair.ansut

/**
 * ANSUT Launcher — source de vérité centralisée du design system « One UI ».
 *
 * Toutes les valeurs de marque (couleurs, transparences, rayons) utilisées par
 * le cœur du launcher (drawer, recherche, dossiers, fast-scroller…) doivent
 * référencer cet objet au lieu de disperser des littéraux #205EB3 / #F18120 /
 * #0E1116 dans le code.
 *
 * Les ressources XML (drawables des widgets, colors.xml) gardent leurs valeurs
 * en ressources (@color/ansut_blue, @drawable/ansut_widget_bg…) : elles doivent
 * rester identiques aux constantes ci-dessous.
 */
object AnsutTheme {

    // ---- Couleurs de marque -------------------------------------------------
    /** Bleu ANSUT (primaire). */
    @JvmField val PRIMARY: Int = 0xFF205EB3.toInt()

    /** Bleu ANSUT foncé (appui, variantes sombres). */
    @JvmField val PRIMARY_DARK: Int = 0xFF1C55A3.toInt()

    /** Orange ANSUT (accent, logo, CTA). */
    @JvmField val ACCENT: Int = 0xFFF18120.toInt()

    // ---- Surfaces « verre » One UI ------------------------------------------
    /** Base RGB de la carte verre foncée (#0E1116), sans alpha. */
    const val GLASS_RGB = 0x0E1116

    /** Carte verre des widgets / protection d'en-tête du drawer (alpha 80 %). */
    @JvmField val SURFACE_GLASS: Int = 0xCC0E1116.toInt()

    /** Scrim du drawer (l'alpha effectif est appliqué par le jeton : 55 %). */
    const val SURFACE_GLASS_BASE = 0x0E1116

    // ---- Textes sur fond verre ----------------------------------------------
    /** Texte principal sur verre (blanc plein). */
    @JvmField val TEXT_PRIMARY: Int = 0xFFFFFFFF.toInt()

    /** Texte secondaire sur verre (70 %). */
    @JvmField val TEXT_SECONDARY: Int = 0xB3FFFFFF.toInt()

    /** Texte tertiaire sur verre (60 %, ex. températures min des widgets). */
    @JvmField val TEXT_TERTIARY: Int = 0x99FFFFFF.toInt()

    /** Libellés d'apps forcés lisibles sur le verre foncé (90 %). */
    @JvmField val LABEL_ON_GLASS: Int = 0xE6FFFFFF.toInt()

    // ---- Décor ---------------------------------------------------------------
    /** Liseré 1dp des cartes verre (15 % blanc). */
    @JvmField val GLASS_STROKE: Int = 0x26FFFFFF.toInt()

    /** Séparateur hairline entre sections du drawer (10 % blanc). */
    @JvmField val DIVIDER: Int = 0x1AFFFFFF.toInt()

    /** Pastille / pill translucide sur verre foncé (recherche, onglets). */
    @JvmField val PILL: Int = 0x33FFFFFF.toInt()

    /** Voile discret sur fond clair (variante jour des pastilles). */
    @JvmField val PILL_LIGHT: Int = 0x22000000.toInt()

    /** Variante « blur » du voile clair. */
    @JvmField val PILL_LIGHT_BLUR: Int = 0x14000000.toInt()

    /** Rayon des cartes One UI, en dp. */
    const val CORNER_RADIUS_DP = 24
}
