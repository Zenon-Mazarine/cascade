package com.bidule.cascade.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.Pro

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Shown when someone picks a Pro style, or when a preview runs out.
 * One-time purchase, no subscription, no ads.
 */
@Composable
fun Paywall(
    style: CascadeStyle,
    previewEnded: Boolean,
    canPreview: Boolean,
    onPreview: () -> Unit,
    onClose: () -> Unit,
) {
    val look = LocalLook.current
    val context = LocalContext.current
    val price by Pro.price.collectAsState()
    val status by Pro.status.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cascade Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Text(
                if (previewEnded) "Your preview of ${style.label} has ended." else "${style.label} is part of Cascade Pro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(18.dp))

            // The four Pro styles, each drawn in its own colors.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CascadeStyle.entries.filter { it.isPro }.forEach { st ->
                    val preview = lookFor(look.mode, look.mono, st)
                    val bg = if (preview.background == Color.Transparent) preview.panel.copy(alpha = 1f) else preview.background
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(44.dp)) { drawOrb(preview) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(st.label, fontSize = 12.sp, fontWeight = if (st == style) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            listOf(
                "Stone, Paper, Neon and Cosmos: each with its own layout, motion, sounds and vibration",
                "Every future style included",
                "One-time purchase. No subscription, no ads",
            ).forEach { line ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(18.dp))

            val buyLabel = when (status) {
                Pro.Status.Loading -> "Connecting to Google Play…"
                Pro.Status.Unavailable -> "Google Play isn't available right now"
                Pro.Status.Pending -> "Purchase pending…"
                Pro.Status.Owned -> "Unlocked. Thank you!"
                Pro.Status.Ready -> price?.let { "Unlock Pro for $it" } ?: "Unlock Pro"
            }
            Button(
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null || !Pro.buy(activity)) {
                        Toast.makeText(context, "Couldn't reach Google Play. Try again in a moment.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = status == Pro.Status.Ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(buyLabel, fontWeight = FontWeight.SemiBold) }

            if (canPreview && !previewEnded) {
                TextButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("Try ${style.label} free for 5 minutes")
                }
            } else if (!canPreview) {
                Text(
                    "You can preview each style once a day.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            TextButton(
                onClick = {
                    Pro.refreshPurchases()
                    Toast.makeText(context, "Checking your purchases…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore purchase", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
        }
        }
    }
}
