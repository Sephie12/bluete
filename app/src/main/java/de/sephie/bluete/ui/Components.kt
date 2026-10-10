package de.sephie.bluete.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val German: Locale = Locale.GERMANY
private val fmtLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM", German)
private val fmtShort = DateTimeFormatter.ofPattern("EE d. MMM", German)
private val fmtNoWeekday = DateTimeFormatter.ofPattern("d. MMM", German)
private val fmtFull = DateTimeFormatter.ofPattern("d. MMMM yyyy", German)

fun LocalDate.long(): String = format(fmtLong)
fun LocalDate.short(): String = format(fmtShort)
fun LocalDate.dayMonth(): String = format(fmtNoWeekday)
fun LocalDate.full(): String = format(fmtFull)
fun LocalDate.monthTitle(): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, German).replaceFirstChar { it.uppercase() } + " $year"

fun range(a: LocalDate, b: LocalDate): String =
    if (a.month == b.month) "${a.dayOfMonth}.–${b.dayMonth()}" else "${a.dayMonth()} – ${b.dayMonth()}"

fun tage(n: Int) = if (n == 1) "1 Tag" else "$n Tage"
fun tagen(n: Int) = if (n == 1) "1 Tag" else "$n Tagen"

/** Eine abgerundete Karte im Bloom-Stil. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}
