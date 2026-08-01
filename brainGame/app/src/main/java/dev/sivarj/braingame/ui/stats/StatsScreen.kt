package dev.sivarj.braingame.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sivarj.braingame.domain.Elo
import dev.sivarj.braingame.domain.SkillRating
import dev.sivarj.braingame.ui.play.PlayViewModel
import kotlin.math.roundToInt

@Composable
fun StatsScreen(viewModel: PlayViewModel, modifier: Modifier = Modifier) {
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Your skills", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Each skill is rated separately, so a weak area can't hide behind a strong one. " +
                "Puzzles are served slightly above your rating, and the weakest skill comes up most.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (ratings.all { it.attempts == 0 }) {
            Text(
                "No puzzles played yet — ratings appear once you start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ratings.sortedByDescending { it.rating }.forEach { rating ->
                SkillRow(rating)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SkillRow(rating: SkillRating) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rating.skill.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        rating.skill.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (rating.attempts == 0) "—" else "${rating.rating}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            RatingBar(rating.rating, played = rating.attempts > 0)

            Spacer(Modifier.height(8.dp))
            Text(
                if (rating.attempts == 0) {
                    "Not played yet"
                } else {
                    "${rating.solved} of ${rating.attempts} solved " +
                        "(${(rating.successRate * 100).roundToInt()}%)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Position of the rating within the usable Elo band, as a simple filled track. */
@Composable
private fun RatingBar(rating: Int, played: Boolean) {
    val span = (Elo.MAX_RATING - Elo.MIN_RATING).toFloat()
    val fraction = if (!played) 0f else ((rating - Elo.MIN_RATING) / span).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}
