package de.sephie.bluete.domain

/** Symptom-Kategorien wie bei Bloom. */
enum class SymptomCategory(val key: String, val label: String) {
    KOERPER("physical", "Körperlich"),
    GEFUEHL("emotional", "Gefühle"),
    BLUTUNG("flow", "Blutung"),
    SONSTIGES("other", "Sonstiges");

    companion object {
        fun fromKey(key: String): SymptomCategory = entries.firstOrNull { it.key == key } ?: SONSTIGES
    }
}

data class SymptomDef(val name: String, val category: SymptomCategory, val bloomName: String?)

/** Die Symptomliste von Bloom, auf Deutsch. */
object SymptomCatalog {
    val builtIn: List<SymptomDef> = listOf(
        SymptomDef("Krämpfe", SymptomCategory.KOERPER, "Cramps"),
        SymptomDef("Kopfschmerzen", SymptomCategory.KOERPER, "Headache"),
        SymptomDef("Blähbauch", SymptomCategory.KOERPER, "Bloating"),
        SymptomDef("Müdigkeit", SymptomCategory.KOERPER, "Fatigue"),
        SymptomDef("Rückenschmerzen", SymptomCategory.KOERPER, "Back Pain"),
        SymptomDef("Brustspannen", SymptomCategory.KOERPER, "Breast Tenderness"),
        SymptomDef("Übelkeit", SymptomCategory.KOERPER, "Nausea"),
        SymptomDef("Pickel", SymptomCategory.KOERPER, "Acne"),
        SymptomDef("Schwindel", SymptomCategory.KOERPER, "Dizziness"),

        SymptomDef("Stimmungsschwankungen", SymptomCategory.GEFUEHL, "Mood Swings"),
        SymptomDef("Reizbarkeit", SymptomCategory.GEFUEHL, "Irritability"),
        SymptomDef("Angst", SymptomCategory.GEFUEHL, "Anxiety"),
        SymptomDef("Traurigkeit", SymptomCategory.GEFUEHL, "Sadness"),
        SymptomDef("Glücklich", SymptomCategory.GEFUEHL, "Happy"),
        SymptomDef("Benebelt", SymptomCategory.GEFUEHL, "Brain Fog"),
        SymptomDef("Antriebslos", SymptomCategory.GEFUEHL, "Low Motivation"),

        SymptomDef("Starke Blutung", SymptomCategory.BLUTUNG, "Heavy Flow"),
        SymptomDef("Mittlere Blutung", SymptomCategory.BLUTUNG, "Medium Flow"),
        SymptomDef("Leichte Blutung", SymptomCategory.BLUTUNG, "Light Flow"),
        SymptomDef("Schmierblutung", SymptomCategory.BLUTUNG, "Spotting"),
        SymptomDef("Klumpen", SymptomCategory.BLUTUNG, "Clots"),

        SymptomDef("Heißhunger", SymptomCategory.SONSTIGES, "Cravings"),
        SymptomDef("Schlaflosigkeit", SymptomCategory.SONSTIGES, "Insomnia"),
        SymptomDef("Hitzewallungen", SymptomCategory.SONSTIGES, "Hot Flashes"),
        SymptomDef("Mehr Appetit", SymptomCategory.SONSTIGES, "Increased Appetite"),
        SymptomDef("Wenig Appetit", SymptomCategory.SONSTIGES, "Low Appetite")
    )

    /** Übersetzt einen Symptomnamen aus einem Bloom-Export ins Deutsche (unbekannte bleiben, wie sie sind). */
    fun fromBloom(name: String): String =
        builtIn.firstOrNull { it.bloomName.equals(name.trim(), ignoreCase = true) }?.name ?: name.trim()

    val severityLabels = listOf("", "sehr leicht", "leicht", "mittel", "stark", "sehr stark")
}
