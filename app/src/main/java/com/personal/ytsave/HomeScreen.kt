package com.personal.ytsave

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val items by Downloader.items.collectAsStateWithLifecycle()
    val ready by Downloader.ready.collectAsStateWithLifecycle()
    val initError by Downloader.initError.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header() }

            if (initError != null) {
                item { ErrorCard("Download engine failed to start: $initError") }
            } else if (!ready) {
                item { EngineStatus() }
            }

            item { LinkInput(vm, enabled = ready) }

            item {
                AnimatedVisibility(visible = vm.fetchState is FetchState.Ready || vm.fetchState is FetchState.Error) {
                    when (val s = vm.fetchState) {
                        is FetchState.Ready -> PreviewCard(
                            meta = s.meta,
                            selected = vm.quality,
                            onSelect = vm::selectQuality,
                            onDownload = vm::download
                        )
                        is FetchState.Error -> ErrorCard(s.message)
                        else -> {}
                    }
                }
            }

            if (items.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Downloads",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { Downloader.clearFinished() }) { Text("Clear finished") }
                    }
                }
                items(items, key = { it.id }) { item ->
                    DownloadRow(item)
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            "YTSave",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Paste a link or share one from YouTube.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EngineStatus() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Preparing download engine…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkInput(vm: MainViewModel, enabled: Boolean) {
    val clipboard = LocalClipboardManager.current
    val loading = vm.fetchState is FetchState.Loading

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = vm.url,
            onValueChange = vm::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://youtube.com/watch?v=…") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let {
                        vm.onUrlChange(it)
                        vm.submit(it)
                    }
                }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste and fetch") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.submit() })
        )
        Button(
            onClick = { vm.submit() },
            enabled = enabled && !loading && vm.url.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("Fetching…")
            } else {
                Text("Fetch video", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(
    meta: VideoMeta,
    selected: Quality,
    onSelect: (Quality) -> Unit,
    onDownload: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = meta.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                if (meta.durationSec > 0) {
                    Text(
                        formatDuration(meta.durationSec),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        meta.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.uploader.isNotBlank()) {
                        Text(
                            meta.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ChipGroup("Video", Quality.entries.filter { !it.isAudio }, selected, onSelect)
                ChipGroup("Audio only", Quality.entries.filter { it.isAudio }, selected, onSelect)

                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download ${selected.label}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    label: String,
    options: List<Quality>,
    selected: Quality,
    onSelect: (Quality) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { q ->
                FilterChip(
                    selected = q == selected,
                    onClick = { onSelect(q) },
                    label = { Text(q.label) }
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.quality} · ${statusText(item)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == Status.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                when (item.status) {
                    Status.Running ->
                        if (item.progress > 0f) LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth()
                        ) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Status.Queued, Status.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {}
                }
            }
            when {
                item.status.isActive -> IconButton(onClick = { Downloader.cancel(item.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                item.status == Status.Done -> Row {
                    IconButton(onClick = {
                        val open = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.savedUri, item.mime ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(open) }
                    }) { Icon(Icons.Default.OpenInNew, contentDescription = "Open") }
                    IconButton(onClick = { Downloader.remove(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove from list")
                    }
                }
                else -> IconButton(onClick = { Downloader.remove(item.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove from list")
                }
            }
        }
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    Status.Queued -> "Waiting…"
    Status.Running ->
        if (item.progress <= 0f) "Starting…"
        else "${(item.progress * 100).toInt()}%" +
            if (item.etaSec > 0) " · ${formatEta(item.etaSec)} left" else ""
    Status.Saving -> "Saving to Downloads…"
    Status.Done -> "Saved in Downloads/YTSave"
    Status.Failed -> item.error ?: "Failed"
    Status.Cancelled -> "Cancelled"
}

private fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatEta(sec: Long): String =
    if (sec >= 60) "${sec / 60}m ${sec % 60}s" else "${sec}s"
