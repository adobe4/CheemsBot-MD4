package com.vinplay.m3u.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vinplay.m3u.data.model.TestStatus

/** Color of the link-test dot. Green OK, yellow UNSTABLE (plays then freezes), red DEAD. */
@Composable
fun statusColor(status: TestStatus): Color = when (status) {
    TestStatus.OK -> Color(0xFF3FBF6A)
    TestStatus.UNSTABLE -> Color(0xFFE6C200) // yellow — starts then freezes/rebuffers
    TestStatus.REDIRECT -> Color(0xFFE0B036)
    TestStatus.DEAD -> Color(0xFFE0483B)
    TestStatus.TIMEOUT -> Color(0xFFE07A3B)
    TestStatus.ERROR -> Color(0xFFB0483B)
    TestStatus.TESTING -> MaterialTheme.colorScheme.primary
    TestStatus.UNTESTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
}

/**
 * Channel logo thumbnail (tvg-logo) with the link-test status as a small corner dot.
 *
 * [selected] drives the selection badge: null means selection mode is off. The badge sits in the
 * top-start corner rather than replacing the thumbnail, so the logo and the test-result dot stay
 * visible (and stay put) while picking channels.
 */
@Composable
fun LogoThumb(logo: String?, status: TestStatus, selected: Boolean? = null) {
    Box(Modifier.size(44.dp)) {
        val shape = RoundedCornerShape(8.dp)
        if (logo.isNullOrBlank()) {
            Box(
                Modifier.matchParentSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        StatusDot(color = statusColor(status), modifier = Modifier.align(Alignment.BottomEnd))

        if (selected != null) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}
