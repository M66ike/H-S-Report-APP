package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val GmesTeal = Color(0xFF0C6E6A)
val GmesDark = Color(0xFF4D4D4D)
val GmesLight = Color(0xFFF3F6F6)

fun displayDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (_: Exception) {
    iso
}

fun fileBase(report: ReportData): String {
    val date = report.visitDate.replace("-", "")
    val project = report.projectName.ifBlank { "H-S-Report" }
        .replace(Regex("[^A-Za-z0-9 _-]"), "")
        .trim()
        .replace(" ", "_")
    return "${date}_${project}"
}

fun createPhotoFile(context: Context, reportId: String, questionId: String): File {
    val dir = File(context.filesDir, "reports/$reportId/photos").apply { mkdirs() }
    return File(dir, "${questionId.replace('.', '_')}_${System.currentTimeMillis()}.jpg")
}

fun copyUriToReportPhoto(context: Context, reportId: String, questionId: String, uri: Uri): String? = try {
    val dir = File(context.filesDir, "reports/$reportId/photos").apply { mkdirs() }
    val ext = guessExtension(context, uri)
    val file = File(dir, "${questionId.replace('.', '_')}_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    } ?: return null
    file.absolutePath
} catch (_: Exception) {
    null
}

private fun guessExtension(context: Context, uri: Uri): String {
    val type = context.contentResolver.getType(uri).orEmpty()
    if (type.contains("png")) return "png"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val name = c.getString(0).orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in listOf("png", "jpg", "jpeg")) return if (ext == "jpeg") "jpg" else ext
        }
    }
    return "jpg"
}

fun saveSignature(context: Context, reportId: String, strokes: List<List<Offset>>, size: IntSize): String? = try {
    if (size.width <= 0 || size.height <= 0) return null
    val width = 1200
    val height = 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val sx = width.toFloat() / size.width
    val sy = height.toFloat() / size.height
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        strokeWidth = 6f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        style = AndroidPaint.Style.STROKE
    }
    strokes.forEach { points ->
        if (points.size >= 2) {
            val path = android.graphics.Path()
            path.moveTo(points.first().x * sx, points.first().y * sy)
            points.drop(1).forEach { path.lineTo(it.x * sx, it.y * sy) }
            canvas.drawPath(path, paint)
        }
    }
    val dir = File(context.filesDir, "reports/$reportId").apply { mkdirs() }
    val file = File(dir, "signature.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    file.absolutePath
} catch (_: Exception) {
    null
}
