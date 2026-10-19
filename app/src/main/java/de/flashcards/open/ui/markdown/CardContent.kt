package de.flashcards.open.ui.markdown

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.rememberMarkdownState
import de.flashcards.open.R
import de.flashcards.open.domain.markdown.CardMarkdown
import de.flashcards.open.domain.markdown.CardSegment
import de.flashcards.open.ui.common.appContainer
import de.flashcards.open.ui.common.focusFrame
import java.io.File

/**
 * Renders a card side: GFM Markdown plus images from `filesDir/media`.
 * Tapping an image opens the full screen view with pinch zoom.
 */
@Composable
fun CardContent(
    markdown: String,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
) {
    val container = appContainer()
    val index by container.media.index.collectAsState()
    val missing = stringResource(R.string.image_missing, "%s")
    val segments = remember(markdown, index, missing) {
        CardMarkdown.segments(markdown, { index[it] }, { missing.replace("%s", it) })
    }
    var zoomPath by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (seg in segments) {
                when (seg) {
                    is CardSegment.Text -> MarkdownText(seg.markdown)
                    is CardSegment.Image -> if (seg.path != null) {
                        AsyncImage(
                            model = File(seg.path),
                            contentDescription = seg.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .focusFrame(RoundedCornerShape(8.dp))
                                .clickable { zoomPath = seg.path },
                        )
                    } else {
                        MissingImage(seg.name)
                    }
                }
            }
        }
    }
    zoomPath?.let { ImageViewer(it) { zoomPath = null } }
}

@Composable
private fun MarkdownText(md: String) {
    val state = rememberMarkdownState(md, immediate = true)
    val t = MaterialTheme.typography
    Markdown(
        markdownState = state,
        modifier = Modifier.fillMaxWidth(),
        imageTransformer = Coil3ImageTransformerImpl,
        annotator = markdownAnnotator(config = markdownAnnotatorConfig(eolAsNewLine = true)),
        typography = markdownTypography(
            h1 = t.headlineMedium, h2 = t.headlineSmall, h3 = t.titleLarge,
            h4 = t.titleMedium, h5 = t.titleSmall, h6 = t.titleSmall,
            text = t.bodyLarge, paragraph = t.bodyLarge, ordered = t.bodyLarge, bullet = t.bodyLarge, list = t.bodyLarge,
        ),
    )
}

@Composable
private fun MissingImage(name: String) {
    val red = MaterialTheme.colorScheme.error
    Text(
        stringResource(R.string.image_missing, name),
        color = red,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .border(1.dp, red, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Full screen with pinch zoom, panning and double tap to reset. */
@Composable
fun ImageViewer(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                        }
                        .transformable(state)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        },
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = Color.White)
                }
            }
        }
    }
}
