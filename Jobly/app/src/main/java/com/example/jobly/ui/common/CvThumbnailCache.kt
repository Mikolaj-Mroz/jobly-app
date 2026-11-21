package com.example.jobly.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.jobly.model.CvItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CvThumbnailCache {
    private val memoryCache = LruCache<String, ImageBitmap>(12)

    suspend fun getOrLoadThumbnail(
        context: Context,
        cv: CvItem,
        fetchPdfBytes: suspend (String) -> Result<ByteArray>
    ): ImageBitmap? {
        val key = buildCacheKey(cv)
        memoryCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val thumbsDir = File(context.cacheDir, "cv_thumbs").apply { mkdirs() }
            val thumbFile = File(thumbsDir, "$key.png")

            // 1. Disk cache hit
            if (thumbFile.exists()) {
                val decoded = BitmapFactory.decodeFile(thumbFile.absolutePath)?.asImageBitmap()
                if (decoded != null) {
                    memoryCache.put(key, decoded)
                    return@withContext decoded
                }
            }

            // 2. Fetch PDF bytes and render page 1 thumbnail
            val pdfBytes = fetchPdfBytes(cv.id).getOrNull() ?: return@withContext null
            val bitmap = renderFirstPageBitmap(context, cv.id, pdfBytes) ?: return@withContext null

            // Clean up older cached thumbnails for this CV id
            val prefix = "${sanitizeKeySegment(cv.id)}_"
            thumbsDir.listFiles()?.forEach { existing ->
                if (existing.name.startsWith(prefix) && existing.name != thumbFile.name) {
                    runCatching { existing.delete() }
                }
            }

            runCatching {
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }

            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(key, imageBitmap)
            imageBitmap
        }
    }

    private fun buildCacheKey(cv: CvItem): String {
        val idPart = sanitizeKeySegment(cv.id)
        val revHash = "${cv.fileName}|${cv.updatedLabel}|${cv.fileSizeBytes ?: 0}|${cv.pageCount ?: 0}"
            .hashCode()
            .toUInt()
            .toString(16)
        return "${idPart}_$revHash"
    }

    private fun sanitizeKeySegment(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "cv" }

    private fun renderFirstPageBitmap(
        context: Context,
        cvId: String,
        pdfBytes: ByteArray
    ): Bitmap? {
        val tempPdf = File(context.cacheDir, "thumb_src_${sanitizeKeySegment(cvId)}_${System.nanoTime()}.pdf")
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            tempPdf.writeBytes(pdfBytes)
            pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null
            val page = renderer.openPage(0)
            try {
                val targetWidth = 540
                val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            runCatching { tempPdf.delete() }
        }
    }
}
