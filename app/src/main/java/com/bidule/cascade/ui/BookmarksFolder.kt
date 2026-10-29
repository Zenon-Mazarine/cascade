package com.bidule.cascade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository

/** A filled bookmark ribbon. */
val BookmarkIcon: ImageVector = materialIcon(name = "Cascade.Bookmark") {
    materialPath {
        moveTo(17f, 3f)
        horizontalLineTo(7f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(16f)
        lineToRelative(7f, -3f)
        lineToRelative(7f, 3f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
    }
}

/**
 * The bookmarks folder: a grid of hand-picked apps. "Edit" makes the icons wiggle;
 * tap one to remove it, or tap + to pick more.
 */
@Composable
fun BookmarksPanel(
    apps: List<AppEntry>,
    editing: Boolean,
    openCount: Int,
    onToggleEdit: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val look = LocalLook.current
    Surface(
        modifier = Modifier.width(320.dp).heightIn(max = 480.dp),
        shape = RoundedCornerShape(30.dp),
        color = look.panel,
        shadowElevation = 16.dp,
    ) {
        Column(
            Modifier
                .padding(top = 16.dp, bottom = 14.dp)
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        ) {
            Row(
                Modifier.padding(start = 22.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BookmarkIcon, contentDescription = null, tint = look.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Bookmarks",
                    style = look.style(22.sp, FontWeight.SemiBold).copy(shadow = null),
                    modifier = Modifier.weight(1f),
                )
                if (apps.isNotEmpty()) {
                    TextButton(onClick = onToggleEdit) {
                        Text(if (editing) "Done" else "Edit", color = look.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (apps.isEmpty()) {
                Text(
                    "Your folder is empty. Tap + to add apps, or long-press any app and choose “Add to bookmarks”.",
                    style = look.style(14.sp, color = look.subtext).copy(shadow = null),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
                    CascadeIn(index, openCount, Modifier.animateItem()) {
                        BookmarkTile(
                            app = app,
                            editing = editing,
                            onClick = { if (editing) onRemove(app.key) else onLaunch(app) },
                        )
                    }
                }
                if (editing || apps.isEmpty()) {
                    item(key = "add") {
                        AddTile(onAdd, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkTile(app: AppEntry, editing: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.bouncy, label = "tilePress")

    // Edit mode: each icon wiggles at a slightly different speed so they don't move in lockstep.
    val wiggle = if (editing) {
        val t = rememberInfiniteTransition(label = "wiggle")
        t.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 120 + (app.key.hashCode() and 0x3F), easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "wiggleAngle",
        ).value
    } else 0f

    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = wiggle
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Image(app.icon, contentDescription = null, colorFilter = look.iconFilter, modifier = Modifier.size(50.dp))
            AnimatedVisibility(
                visible = editing,
                enter = scaleIn(Motion.bouncy),
                exit = scaleOut(tween(120)) + fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp),
            ) {
                Box(
                    Modifier.size(20.dp).background(look.text, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = look.panel.copy(alpha = 1f), modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = look.style(12.sp).copy(shadow = null),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(look.pressed)
                .border(BorderStroke(1.5.dp, look.subtext.copy(alpha = 0.5f)), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add apps", tint = look.text)
        }
        Spacer(Modifier.height(6.dp))
        Text("Add", style = look.style(12.sp).copy(shadow = null))
    }
}

/** Full-screen list of every app with a check circle: tap to add to or remove from the folder. */
@Composable
fun BookmarkPicker(
    apps: List<AppEntry>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    val look = LocalLook.current
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = AppRepository.normalize(query.trim())
        if (q.isEmpty()) apps
        else apps.filter { it.searchKey.contains(q) }.sortedBy { if (it.searchKey.startsWith(q)) 0 else 1 }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(look.overlay)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Add to bookmarks",
                    style = look.style(22.sp, FontWeight.SemiBold).copy(shadow = null),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                TextButton(onClick = onDone) {
                    Text("Done", color = look.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "${selected.size} selected",
                style = look.style(13.sp, color = look.subtext).copy(shadow = null),
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.key }) { app ->
                    val on = app.key in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggle(app.key) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(app.icon, contentDescription = null, colorFilter = look.iconFilter, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            app.label,
                            style = look.style(17.sp).copy(shadow = null),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        CheckCircle(on)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val look = LocalLook.current
    val fill by animateColorAsState(if (checked) look.accent else Color.Transparent, label = "checkFill")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(2.dp, if (checked) look.accent else look.subtext.copy(alpha = 0.6f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = checked, enter = scaleIn(Motion.bouncy), exit = scaleOut(tween(120))) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
