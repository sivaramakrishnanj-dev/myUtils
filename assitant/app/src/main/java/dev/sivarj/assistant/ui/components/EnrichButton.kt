package dev.sivarj.assistant.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.ai.ContentType
import dev.sivarj.assistant.ai.EnrichResult
import kotlinx.coroutines.launch

/**
 * A text button that calls Bedrock to enrich the given [rawText] and delivers
 * the result through [onEnriched]. Shows a spinner while the request is in
 * flight, and a one-shot error message on failure.
 */
@Composable
fun EnrichButton(
    rawText: String,
    contentType: ContentType,
    onEnriched: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AssistantApp
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TextButton(
        enabled = rawText.isNotBlank() && !loading,
        onClick = {
            loading = true
            error = null
            scope.launch {
                when (val result = app.enrichmentService.enrich(rawText, contentType)) {
                    is EnrichResult.Success -> onEnriched(result.text)
                    is EnrichResult.Failure -> error = result.error
                }
                loading = false
            }
        },
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("Enriching…")
        } else {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Enrich with AI")
        }
    }
    if (error != null) {
        Text(error!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}
