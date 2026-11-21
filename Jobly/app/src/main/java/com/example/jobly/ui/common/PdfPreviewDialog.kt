package com.example.jobly.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.jobly.ui.theme.BadgeError
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SurfaceWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface PdfRenderResult {
    data object Loading : PdfRenderResult
    data class Success(val pages: List<ImageBitmap>) : PdfRenderResult
    data class Error(val message: String) : PdfRenderResult
}

@Composable
fun PdfPreviewDialog(
    fileName: String,
    pdfBytes: ByteArray,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var renderState by remember(fileName, pdfBytes) { mutableStateOf<PdfRenderResult>(PdfRenderResult.Loading) }
    var cachedPdfFile by remember(fileName, pdfBytes) { mutableStateOf<File?>(null) }

    LaunchedEffect(fileName, pdfBytes) {
        renderState = PdfRenderResult.Loading
        val (file, result) = withContext(Dispatchers.IO) {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]+"), "-").ifBlank { "cv-preview.pdf" }
            val cvsCacheDir = File(context.cacheDir, "cvs").apply { mkdirs() }
            val tempFile = File(cvsCacheDir, "preview_$safeName")
            tempFile.writeBytes(pdfBytes)
            tempFile to renderPdfPages(tempFile)
        }
        cachedPdfFile = file
        renderState = result
    }

    DisposableEffect(cachedPdfFile) {
        onDispose {
            runCatching { cachedPdfFile?.delete() }
        }
    }

    val sizeLabel = formatPdfSize(pdfBytes.size)
    val pageLabel = when (val state = renderState) {
        is PdfRenderResult.Success -> "${state.pages.size} page(s) • $sizeLabel"
        else -> sizeLabel
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = fileName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = pageLabel, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 440.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = renderState) {
                    is PdfRenderResult.Loading -> {
                        CircularProgressIndicator()
                    }
                    is PdfRenderResult.Error -> {
                        Text(text = state.message, color = BadgeError, fontSize = 14.sp)
                    }
                    is PdfRenderResult.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(state.pages) { idx, bitmap ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
                                        .background(SurfaceWhite)
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "Page ${idx + 1} / ${state.pages.size}",
                                            fontSize = 11.sp,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cachedPdfFile != null,
                onClick = {
                    val file = cachedPdfFile ?: return@TextButton
                    try {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            "No external PDF viewer app installed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Text("Open externally", color = OnSurface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun renderPdfPages(file: File): PdfRenderResult {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        if (count <= 0) {
            return PdfRenderResult.Error("PDF contains zero pages")
        }
        val rendered = ArrayList<ImageBitmap>(count)
        for (i in 0 until count) {
            val page = renderer.openPage(i)
            try {
                val targetWidth = 1080
                val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                rendered += bitmap.asImageBitmap()
            } finally {
                page.close()
            }
        }
        PdfRenderResult.Success(rendered)
    } catch (t: Throwable) {
        PdfRenderResult.Error(t.message ?: "Failed to render PDF preview")
    } finally {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
    }
}

private fun formatPdfSize(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
