package com.bidule.cascade.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sheet that opens from the style button: every style as a little live-colored card,
 * and at the end, "Original": hand the phone back to its own home screen.
 */
@Composable
fun StylePickerSheet(
    onPick: (Offset, CascadeStyle) -> Unit,
    onOriginal: () -> Unit,
    onClose: () -> Unit,
) {
    val look = LocalLook.current
    var confirmOriginal by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(look.panel.copy(alpha = 1f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .padding(vertical = 14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .background(look.text.copy(alpha = 0.25f), RoundedCornerShape(50))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Choose a style",
                style = look.display(26.sp).copy(shadow = null),
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Text(
                "Each one changes the layout, motion, sounds and vibration.",
                style = look.style(13.sp, color = look.subtext).copy(shadow = null),
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(CascadeStyle.entries) { st ->
                    StyleCard(
                        preview = lookFor(look.mode, look.mono, st),
                        selected = st == look.kind,
                        onClick = { center -> onPick(center, st) },
                    )
                }
                item {
                    OriginalCard(onClick = { confirmOriginal = true })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmOriginal) {
        AlertDialog(
            onDismissRequest = { confirmOriginal = false },
            title = { Text("Return to your original home screen?") },
            text = {
                Text(
                    "Cascade steps aside and your phone's own home screen comes back exactly as you left it: " +
                        "icons, folders and widgets. You can come back any time by opening Cascade from your apps.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOriginal = false
                    onOriginal()
                }) { Text("Return") }
            },
            dismissButton = { TextButton(onClick = { confirmOriginal = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StyleCard(preview: Look, selected: Boolean, onClick: (Offset) -> Unit) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val bg = if (preview.background == Color.Transparent) preview.panel.copy(alpha = 1f) else preview.background
    Column(
        Modifier
            .width(132.dp)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clip(RoundedCornerShape(20.dp))
            .border(
                if (selected) BorderStroke(2.5.dp, preview.accent) else BorderStroke(1.dp, preview.text.copy(alpha = 0.12f)),
                RoundedCornerShape(20.dp),
            )
            .background(bg)
            .clickable { onClick(center) }
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(62.dp)) { drawOrb(preview) }
        }
        Text(
            preview.kind.label,
            style = preview.display(22.sp).copy(shadow = null, color = preview.text, fontWeight = FontWeight.Normal),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            preview.kind.blurb,
            style = preview.style(11.sp, color = preview.subtext).copy(shadow = null),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(44.dp),
        )
    }
}

@Composable
private fun OriginalCard(onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, look.text.copy(alpha = 0.25f)), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = look.text, modifier = Modifier.size(44.dp))
        }
        Text("Original", style = look.style(20.sp, FontWeight.SemiBold).copy(shadow = null), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            "Your phone's own home screen, exactly as it was",
            style = look.style(11.sp, color = look.subtext).copy(shadow = null),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(44.dp),
        )
    }
}
