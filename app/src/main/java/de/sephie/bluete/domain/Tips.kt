package de.sephie.bluete.domain

data class PhaseTips(
    val description: String,
    val encouragement: String,
    val exerciseTip: String,
    val exerciseExamples: List<String>,
    val nutritionTip: String,
    val keyNutrients: List<String>,
    val foods: List<String>
)

/** Die Tipps aus Bloom, auf Deutsch. */
object Tips {
    fun forPhase(phase: Phase): PhaseTips = when (phase) {
        Phase.MENSTRUATION -> PhaseTips(
            description = "Deine Periode ist da. Lass es ruhig angehen, trink genug und hör auf deinen Körper.",
            encouragement = "Dein Körper leistet gerade viel. Ausruhen ist keine Faulheit, sondern Erholung. Diese Phase geht vorbei. 💛",
            exerciseTip = "Sanfte Bewegung hilft gegen Krämpfe und hebt die Stimmung.",
            exerciseExamples = listOf("Spazieren", "Yoga", "Dehnen", "Ruhiges Schwimmen"),
            nutritionTip = "Eisenreiche und entzündungshemmende Lebensmittel helfen, Verlorenes wieder aufzufüllen.",
            keyNutrients = listOf("Eisen", "Magnesium", "Omega-3", "Vitamin C"),
            foods = listOf("Spinat", "Linsen", "Zartbitterschokolade", "Lachs", "Ingwertee", "Brühe")
        )
        Phase.FOLLIKEL -> PhaseTips(
            description = "Die Energie steigt! Dein Körper bereitet sich auf den Eisprung vor. Gute Zeit für neue Projekte.",
            encouragement = "Du hast wieder mehr Energie. Das ist deine Woche für Ziele, Ideen und Neues. ✨",
            exerciseTip = "Dein Körper erholt sich jetzt schneller und baut leichter Muskeln auf.",
            exerciseExamples = listOf("Krafttraining", "HIIT", "Laufen", "Tanzen", "Klettern"),
            nutritionTip = "Mageres Eiweiß und komplexe Kohlenhydrate geben dir Kraft. Gute Zeit für neue Rezepte.",
            keyNutrients = listOf("B-Vitamine", "Zink", "Vitamin E", "Probiotika"),
            foods = listOf("Eier", "Avocado", "Fermentiertes", "Hähnchen", "Quinoa", "Brokkoli")
        )
        Phase.EISPRUNG -> PhaseTips(
            description = "Energie und Selbstvertrauen auf dem Höhepunkt. Du bist wahrscheinlich geselliger und kreativer. Das ist dein fruchtbares Fenster.",
            encouragement = "Du strahlst gerade Stärke und Selbstvertrauen aus. Genieß es! 🌟",
            exerciseTip = "Jetzt bist du am stärksten und hast die meiste Ausdauer.",
            exerciseExamples = listOf("Intensives Training", "Spinning", "Sprints", "Gruppenkurse", "Boxen"),
            nutritionTip = "Antioxidantienreiche, frische Lebensmittel unterstützen dich. Trink besonders viel.",
            keyNutrients = listOf("Antioxidantien", "B-Vitamine", "Zink", "Glutathion"),
            foods = listOf("Beeren", "Paprika", "Rohkost", "Nüsse", "Samen", "Kokoswasser")
        )
        Phase.LUTEAL -> PhaseTips(
            description = "Der Zyklus klingt ab. Vielleicht hast du Lust auf Wohlfühlessen und brauchst mehr Ruhe. Sei sanft zu dir.",
            encouragement = "Es ist in Ordnung, langsamer zu machen. Du musst nicht jeden Tag produktiv sein, um wertvoll zu sein. 🌙",
            exerciseTip = "Fahr die Intensität langsam herunter und achte auf Müdigkeit.",
            exerciseExamples = listOf("Pilates", "Schwimmen", "Spazieren", "Leichte Gewichte", "Entspannungsyoga"),
            nutritionTip = "Komplexe Kohlenhydrate und Magnesium helfen gegen Heißhunger. Nicht verzichten, sondern gut versorgen.",
            keyNutrients = listOf("Magnesium", "Calcium", "Vitamin B6", "Ballaststoffe"),
            foods = listOf("Süßkartoffeln", "Vollkornreis", "Zartbitterschokolade", "Bananen", "Haferflocken", "Kamillentee")
        )
    }
}
