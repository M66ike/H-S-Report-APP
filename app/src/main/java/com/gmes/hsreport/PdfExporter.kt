package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

object PdfExporter {
    private const val PAGE_W = 595f // A4 portrait points
    private const val PAGE_H = 842f
    private val teal = Color.rgb(12, 110, 106)
    private val dark = Color.rgb(80, 80, 80)
    private val lightGray = Color.rgb(242, 242, 242)
    private val acceptableGreen = Color.rgb(124, 221, 18)
    private val improveYellow = Color.rgb(255, 230, 0)

    fun create(context: Context, report: ReportData): ByteArray {
        val pdf = PdfDocument()
        drawPage1(pdf, context, report)
        drawPage2(pdf, context, report)
        drawPage3(pdf, context, report)
        return ByteArrayOutputStream().use { out ->
            pdf.writeTo(out)
            pdf.close()
            out.toByteArray()
        }
    }

    private fun drawPage1(pdf: PdfDocument, context: Context, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        val c = page.canvas
        header(c, context, report, 1)
        details(c, report, 45f, 140f)
        drawSection(c, report, ReportTemplate.section("policy"), 148f, 302f)
        drawEvidenceGrid(c, report, listOf("1.01", "1.02", "1.03", "1.04", "1.05", "1.06"), 343f, 3, 735f, 57f, 538f)
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage2(pdf: PdfDocument, context: Context, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 2).create())
        val c = page.canvas
        header(c, context, report, 2)
        drawSection(c, report, ReportTemplate.section("ppe"), 45f, 226f)
        drawEvidenceGrid(c, report, listOf("2.02", "2.03"), 252f, 2, 441f, 135f, 460f)
        drawSection(c, report, ReportTemplate.section("equipment"), 469f, 590f)
        drawSection(c, report, ReportTemplate.section("operational"), 598f, 771f)
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage3(pdf: PdfDocument, context: Context, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 3).create())
        val c = page.canvas
        header(c, context, report, 3)
        drawSection(c, report, ReportTemplate.section("client"), 45f, 188f)
        c.drawLine(14f, 197f, PAGE_W - 14f, 197f, linePaint())
        drawScoreChart(c, report, RectF(66f, 218f, 548f, 490f))
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun header(c: Canvas, context: Context, report: ReportData, page: Int) {
        drawBitmapFit(c, BrandAssets.gmes(context), RectF(14f, 5f, 112f, 37f))
        drawBitmapFit(c, BrandAssets.thinkSmart(context), RectF(418f, 7f, 572f, 35f))
        fittedText(c, "Group Management Electrical Surveys", PAGE_W / 2f, 16f, 275f, 9.2f, 7f, bold = true, align = Paint.Align.CENTER)
        fittedText(c, "Site Visit Report - ${report.projectName.ifBlank { "New Report" }}", PAGE_W / 2f, 31f, 275f, 10.2f, 7f, bold = true, align = Paint.Align.CENTER)
        text(c, "Page $page of 3", PAGE_W - 14f, 39f, 5.8f, Color.DKGRAY, align = Paint.Align.RIGHT)
    }

    private fun details(c: Canvas, report: ReportData, top: Float, bottom: Float) {
        val left = 14f
        val scoreLeft = 465f
        val right = PAGE_W - 14f
        val tableRight = scoreLeft - 4f
        val rowH = (bottom - top) / 4f
        val labelW = 128f
        val mid = 287f
        val secondLabelW = 68f

        outline(c, RectF(left, top, tableRight, bottom))
        repeat(3) { i -> c.drawLine(left, top + rowH * (i + 1), tableRight, top + rowH * (i + 1), linePaint()) }
        c.drawLine(left + labelW, top, left + labelW, bottom, linePaint())
        c.drawLine(mid, top, mid, bottom, linePaint())
        c.drawLine(mid + secondLabelW, top, mid + secondLabelW, bottom, linePaint())

        val labels = listOf("Client:", "Project Name:", "Person Completing Report & Role:", "GMES Site Manager/Supervisor & Role:")
        val values = listOf(report.client, report.projectName, join(report.personName, report.personRole), join(report.managerName, report.managerRole))
        val labels2 = listOf("Site Visit Date:", "GMES Project No:", "Sector:", "Territory:")
        val values2 = listOf(displayDate(report.visitDate), report.projectNo, report.sector, report.territory)

        for (i in 0..3) {
            val y0 = top + i * rowH
            val y = y0 + rowH * 0.65f
            fill(c, RectF(left, y0, left + labelW, y0 + rowH), teal)
            fittedText(c, labels[i], left + 4f, y, labelW - 8f, 5.7f, 4.1f, Color.WHITE, true)
            fittedText(c, values[i], left + labelW + 5f, y, mid - left - labelW - 10f, 5.8f, 4f)
            fill(c, RectF(mid, y0, mid + secondLabelW, y0 + rowH), teal)
            fittedText(c, labels2[i], mid + 3f, y, secondLabelW - 6f, 5.5f, 3.9f, Color.WHITE, true)
            fittedText(c, values2[i], mid + secondLabelW + 4f, y, tableRight - mid - secondLabelW - 8f, 5.8f, 4f)
        }

        outline(c, RectF(scoreLeft, top, right, bottom))
        text(c, "Overall Score", (scoreLeft + right) / 2f, top + 12f, 6.2f, bold = true, align = Paint.Align.CENTER)
        donut(c, (scoreLeft + right) / 2f, top + 53f, 27f, report.overallPercent())
    }

    private fun drawSection(c: Canvas, report: ReportData, section: SectionDef, top: Float, bottom: Float) {
        val left = 14f
        val right = PAGE_W - 14f
        val scored = section.scored
        val headerH = 13f
        val reviewerH = if (section.id == "client") 31f else 31f
        val commentH = when (section.id) {
            "client" -> 45f
            "operational" -> 39f
            else -> 38f
        }
        val summaryW = if (scored) 94f else 0f
        val contentRight = right - summaryW
        val questionBottom = bottom - commentH - reviewerH
        val rowH = (questionBottom - top - headerH) / section.questions.size.toFloat()
        val itemX = left + 25f
        val awardX = if (scored) right - 178f else right - 100f
        val availableX = if (scored) right - 136f else right
        val scoreX = if (scored) right - 94f else right
        val ratingX = if (scored) right - 55f else right

        outline(c, RectF(left, top, right, bottom))
        fill(c, RectF(left, top, itemX, top + headerH), lightGray)
        fill(c, RectF(itemX, top, awardX, top + headerH), if (section.id == "operational" || section.id == "client") teal else dark)
        fill(c, RectF(awardX, top, if (scored) availableX else right, top + headerH), lightGray)
        if (scored) {
            fill(c, RectF(availableX, top, scoreX, top + headerH), lightGray)
            fill(c, RectF(scoreX, top, ratingX, top + headerH), lightGray)
            fill(c, RectF(ratingX, top, right, top + headerH), lightGray)
        }

        text(c, "Item", left + 3f, top + 9f, 5.4f, bold = true)
        val sectionHeader = if (section.subtitle.isBlank()) section.title else "${section.title}\n(${section.subtitle})"
        wrappedTextAuto(c, sectionHeader, itemX + 2f, top + 1f, awardX - itemX - 4f, headerH - 1f, 5.4f, 3.5f, Paint.Align.CENTER, true, Color.WHITE)
        text(c, "Award", (awardX + availableX) / 2f, top + 9f, 5.1f, bold = true, align = Paint.Align.CENTER)
        if (scored) {
            wrappedTextAuto(c, "Available Score", availableX, top + 1f, scoreX - availableX, headerH - 1f, 4.8f, 3.4f, Paint.Align.CENTER, true)
            text(c, "Score", (scoreX + ratingX) / 2f, top + 9f, 4.7f, bold = true, align = Paint.Align.CENTER)
            text(c, "Rating", (ratingX + right) / 2f, top + 9f, 4.7f, bold = true, align = Paint.Align.CENTER)
        }

        var y = top + headerH
        section.questions.forEach { q ->
            val next = y + rowH
            c.drawLine(left, next, if (scored) scoreX else right, next, linePaint())
            c.drawLine(itemX, y, itemX, next, linePaint())
            c.drawLine(awardX, y, awardX, next, linePaint())
            if (scored) {
                c.drawLine(availableX, y, availableX, next, linePaint())
                c.drawLine(scoreX, y, scoreX, next, linePaint())
            }
            fittedText(c, q.id, left + 2f, y + rowH * 0.66f, itemX - left - 4f, 5f, 3.5f)
            wrappedTextAuto(c, q.text, itemX + 2f, y + 1f, awardX - itemX - 4f, rowH - 2f, 4.8f, 3.2f)
            fittedText(c, report.answers[q.id].orEmpty(), awardX + 2f, y + rowH * 0.66f, if (scored) availableX - awardX - 4f else right - awardX - 4f, 4.9f, 3.3f)
            if (scored) {
                val pct = ((q.weight ?: 0.0) * 100.0).toInt()
                text(c, "$pct%", (availableX + scoreX) / 2f, y + rowH * 0.66f, 4.7f, align = Paint.Align.CENTER)
            }
            y = next
        }

        if (scored) {
            val pct = report.sectionPercent(section)
            fill(c, RectF(scoreX, top + headerH, ratingX, questionBottom), if (pct > 80) acceptableGreen else improveYellow)
            outline(c, RectF(scoreX, top + headerH, ratingX, questionBottom))
            outline(c, RectF(ratingX, top + headerH, right, questionBottom))
            text(c, "$pct%", (scoreX + ratingX) / 2f, (top + headerH + questionBottom) / 2f + 2f, 5.5f, bold = true, align = Paint.Align.CENTER)
            wrappedTextAuto(c, if (pct > 80) "Acceptable" else "To Improve", ratingX + 1f, (top + headerH + questionBottom) / 2f - 8f, right - ratingX - 2f, 18f, 4.4f, 3.1f, Paint.Align.CENTER)
        }

        val commentTop = questionBottom
        val reviewTop = bottom - reviewerH
        c.drawLine(left, commentTop, right, commentTop, linePaint())
        c.drawLine(left, reviewTop, right, reviewTop, linePaint())
        if (scored) c.drawLine(contentRight, commentTop, contentRight, bottom, linePaint())

        val commentLabel = when (section.id) {
            "operational" -> "Comments on Operation items:"
            "client" -> "Comments on Client items:"
            else -> "Comments on ${section.title.substringAfter("- ")}:"
        }
        text(c, commentLabel, left + 2f, commentTop + 8f, 5f, bold = true)
        wrappedTextAuto(c, report.comments[section.id].orEmpty(), left + 2f, commentTop + 9f, (if (scored) contentRight else right) - left - 4f, commentH - 10f, 4.9f, 3f)

        val reviewerRight = if (scored) contentRight else right
        val split = left + (reviewerRight - left) * 0.32f
        c.drawLine(split, reviewTop, split, bottom, linePaint())
        text(c, "Reviewer - Print Name", left + 2f, reviewTop + 8f, 4.7f, bold = true)
        text(c, reviewerDisplay(report), left + 50f, reviewTop + 22f, 5.2f, align = Paint.Align.CENTER)
        text(c, "Reviewer - Signature", split + 2f, reviewTop + 8f, 4.7f, bold = true)
        drawSignature(c, report.signaturePath, RectF(split + 65f, reviewTop + 2f, reviewerRight - 5f, bottom - 2f))

        if (scored) {
            val pct = report.sectionPercent(section)
            val weight = (section.questions.sumOf { it.weight ?: 0.0 } * 100.0).toInt()
            text(c, "$weight% of total score", contentRight + summaryW / 2f, commentTop + 9f, 4.4f, align = Paint.Align.CENTER)
            donut(c, contentRight + summaryW / 2f, commentTop + 31f, 17f, pct)
            val label = when (section.id) {
                "policy" -> "Policy\nDocuments"
                "ppe" -> "PPE/Training/\nInspections"
                "equipment" -> "Equipment"
                "operational" -> "Operational\nScore"
                else -> section.title.substringAfter("- ").trim()
            }
            wrappedTextAuto(c, label, contentRight + 2f, reviewTop + 3f, summaryW - 4f, reviewerH - 5f, 4.8f, 3.4f, Paint.Align.CENTER, true)
        }
    }

    private fun drawEvidenceGrid(c: Canvas, report: ReportData, ids: List<String>, top: Float, cols: Int, bottom: Float, left: Float, right: Float) {
        val gap = 11f
        val rows = (ids.size + cols - 1) / cols
        val w = (right - left - gap * (cols - 1)) / cols
        val h = (bottom - top - gap * (rows - 1)) / rows
        ids.forEachIndexed { i, id ->
            val col = i % cols
            val row = i / cols
            val rect = RectF(left + col * (w + gap), top + row * (h + gap), left + col * (w + gap) + w, top + row * (h + gap) + h)
            fill(c, rect, teal)
            val inner = RectF(rect.left + 4f, rect.top + 15f, rect.right - 4f, rect.bottom - 4f)
            fill(c, RectF(rect.left + 4f, rect.top + 2f, rect.right - 4f, rect.top + 14f), dark)
            text(c, id, rect.centerX(), rect.top + 10.5f, 5.4f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
            val path = report.photos[id]
            if (!path.isNullOrBlank()) drawPhoto(c, path, inner) else {
                fill(c, inner, Color.WHITE)
                text(c, "No photo", inner.centerX(), inner.centerY(), 5.5f, Color.GRAY, align = Paint.Align.CENTER)
            }
        }
    }

    private fun drawScoreChart(c: Canvas, report: ReportData, bounds: RectF) {
        val sections = listOf(
            "Overall Score" to report.overallPercent(),
            "Policy Documents" to report.sectionPercent(ReportTemplate.section("policy")),
            "PPE/Training/Inspections" to report.sectionPercent(ReportTemplate.section("ppe")),
            "Equipment" to report.sectionPercent(ReportTemplate.section("equipment")),
            "Operational" to report.sectionPercent(ReportTemplate.section("operational"))
        )
        val chartTop = bounds.top + 8f
        val chartBottom = bounds.bottom - 34f
        val usable = chartBottom - chartTop
        for (pct in 0..100 step 10) {
            val y = chartBottom - usable * pct / 100f
            c.drawLine(bounds.left + 25f, y, bounds.right, y, linePaint().apply { color = Color.LTGRAY; strokeWidth = 0.45f })
            text(c, "$pct%", bounds.left + 20f, y + 2f, 4.3f, Color.DKGRAY, align = Paint.Align.RIGHT)
        }
        val plotLeft = bounds.left + 35f
        val step = (bounds.right - plotLeft) / sections.size
        sections.forEachIndexed { i, (name, pct) ->
            val x = plotLeft + i * step + step * 0.2f
            val barW = step * 0.6f
            val filledH = usable * pct / 100f
            val emptyH = usable - filledH
            fill(c, RectF(x, chartTop, x + barW, chartTop + emptyH), Color.rgb(232, 232, 232))
            fill(c, RectF(x, chartTop + emptyH, x + barW, chartBottom), teal)
            outline(c, RectF(x, chartTop, x + barW, chartBottom))
            wrappedTextAuto(c, name, x - step * 0.1f, chartBottom + 5f, step * 0.8f, 25f, 4.2f, 3.3f, Paint.Align.CENTER)
        }
        fill(c, RectF(bounds.centerX() - 62f, bounds.bottom - 6f, bounds.centerX() - 56f, bounds.bottom), teal)
        text(c, "Completed %", bounds.centerX() - 52f, bounds.bottom, 4f)
        fill(c, RectF(bounds.centerX() + 10f, bounds.bottom - 6f, bounds.centerX() + 16f, bounds.bottom), Color.rgb(232, 232, 232))
        text(c, "Missing %", bounds.centerX() + 20f, bounds.bottom, 4f)
    }

    private fun donut(c: Canvas, cx: Float, cy: Float, radius: Float, percent: Int) {
        val stroke = radius * 0.38f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.LTGRAY }
        c.drawCircle(cx, cy, radius, p)
        p.color = teal
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, percent * 3.6f, false, p)
        text(c, "$percent%", cx, cy + 3f, radius * 0.34f, bold = false, align = Paint.Align.CENTER)
    }

    private fun drawPhoto(c: Canvas, path: String, dest: RectF) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = loadOrientedBitmap(file) ?: return
        fill(c, dest, Color.WHITE)
        val scale = min(dest.width() / bitmap.width.toFloat(), dest.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val target = RectF(dest.left + (dest.width() - w) / 2f, dest.top + (dest.height() - h) / 2f, dest.left + (dest.width() - w) / 2f + w, dest.top + (dest.height() - h) / 2f + h)
        c.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }

    private fun loadOrientedBitmap(file: File): Bitmap? {
        var bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp
    }

    private fun drawSignature(c: Canvas, path: String, dest: RectF) {
        if (path.isBlank()) return
        val file = File(path)
        if (!file.exists()) return
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val scale = min(dest.width() / bmp.width.toFloat(), dest.height() / bmp.height.toFloat())
        val w = bmp.width * scale
        val h = bmp.height * scale
        c.drawBitmap(bmp, null, RectF(dest.left + (dest.width() - w) / 2f, dest.top + (dest.height() - h) / 2f, dest.left + (dest.width() - w) / 2f + w, dest.top + (dest.height() - h) / 2f + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
    }

    private fun drawBitmapFit(c: Canvas, bitmap: Bitmap?, dest: RectF) {
        bitmap ?: return
        val scale = min(dest.width() / bitmap.width.toFloat(), dest.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        c.drawBitmap(bitmap, null, RectF(dest.left + (dest.width() - w) / 2f, dest.top + (dest.height() - h) / 2f, dest.left + (dest.width() - w) / 2f + w, dest.top + (dest.height() - h) / 2f + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun footer(c: Canvas, code: String) = text(c, code, 14f, PAGE_H - 9f, 4.2f, Color.DKGRAY)
    private fun fill(c: Canvas, r: RectF, color: Int) = c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color })
    private fun outline(c: Canvas, r: RectF) = c.drawRect(r, linePaint())
    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 90, 90); style = Paint.Style.STROKE; strokeWidth = 0.6f }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = Color.BLACK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        if (value.isBlank()) return
        c.drawText(value, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT })
    }

    private fun fittedText(c: Canvas, value: String, x: Float, y: Float, width: Float, preferredSize: Float, minSize: Float, color: Int = Color.BLACK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        if (value.isBlank() || width <= 0f) return
        var size = preferredSize
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        while (size > minSize) { p.textSize = size; if (p.measureText(value) <= width) break; size -= 0.2f }
        p.textSize = size.coerceAtLeast(minSize)
        c.drawText(value, x, y, p)
    }

    private fun wrappedTextAuto(c: Canvas, value: String, x: Float, y: Float, width: Float, height: Float, preferredSize: Float, minSize: Float, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false, color: Int = Color.BLACK) {
        if (value.isBlank() || width <= 0f || height <= 0f) return
        var size = preferredSize
        var lines: List<String>
        var lineH: Float
        while (true) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
            lines = layoutLines(value, p, width)
            lineH = size + 1f
            if (lines.size * lineH <= height || size <= minSize) break
            size = (size - 0.2f).coerceAtLeast(minSize)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        val maxLines = (height / lineH).toInt().coerceAtLeast(1)
        val visible = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines && visible.isNotEmpty()) visible[visible.lastIndex] = visible.last().trimEnd().removeSuffix("…") + "…"
        visible.forEachIndexed { i, line ->
            val xx = when (align) { Paint.Align.CENTER -> x + width / 2f; Paint.Align.RIGHT -> x + width; else -> x }
            c.drawText(line, xx, y + size + i * lineH, p)
        }
    }

    private fun layoutLines(value: String, paint: Paint, width: Float): List<String> {
        val result = mutableListOf<String>()
        value.replace("\r", "").split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) result += "" else {
                var line = ""
                paragraph.trim().split(Regex("\\s+")).forEach { word ->
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (paint.measureText(candidate) > width && line.isNotBlank()) { result += line; line = word } else line = candidate
                }
                if (line.isNotBlank()) result += line
            }
        }
        return result
    }

    private fun reviewerDisplay(report: ReportData): String {
        val name = report.reviewerName.ifBlank { report.personName }.trim()
        val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts.first().firstOrNull()?.uppercaseChar() ?: ' '}.${parts.last()}" else name
    }

    private fun join(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.joinToString(" - ")
    private fun displayDate(iso: String): String = try { val p = iso.split("-"); "${p[2]}/${p[1]}/${p[0]}" } catch (_: Exception) { iso }
}
