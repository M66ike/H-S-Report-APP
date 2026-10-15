package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object PdfExporter {
    private const val PAGE_W = 842f // A4 landscape points
    private const val PAGE_H = 595f
    private val teal = Color.rgb(12, 110, 106)
    private val dark = Color.rgb(80, 80, 80)
    private val lightGray = Color.rgb(242, 242, 242)

    fun create(context: Context, report: ReportData): ByteArray {
        val pdf = PdfDocument()
        drawPage1(pdf, report)
        drawPage2(pdf, report)
        drawPage3(pdf, report)
        return ByteArrayOutputStream().use { out ->
            pdf.writeTo(out)
            pdf.close()
            out.toByteArray()
        }
    }

    private fun drawPage1(pdf: PdfDocument, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        val c = page.canvas
        header(c, report, 1)
        details(c, report, 42f)
        val policy = ReportTemplate.section("policy")
        drawSection(c, report, policy, 115f, 235f, includeReviewer = true)
        drawEvidenceGrid(c, report, listOf("1.01", "1.02", "1.03", "1.04", "1.05", "1.06"), top = 300f, cols = 3, bottom = 570f)
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage2(pdf: PdfDocument, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 2).create())
        val c = page.canvas
        header(c, report, 2)
        val ppe = ReportTemplate.section("ppe")
        val equipment = ReportTemplate.section("equipment")
        val operational = ReportTemplate.section("operational")
        drawSection(c, report, ppe, 42f, 160f, includeReviewer = true)
        drawEvidenceGrid(c, report, listOf("2.02", "2.03"), top = 175f, cols = 2, bottom = 315f, left = 190f, right = 650f)
        drawSection(c, report, equipment, 330f, 415f, includeReviewer = true)
        drawSection(c, report, operational, 430f, 555f, includeReviewer = true)
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage3(pdf: PdfDocument, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 3).create())
        val c = page.canvas
        header(c, report, 3)
        drawSection(c, report, ReportTemplate.section("client"), 42f, 205f, includeReviewer = true)
        drawScoreChart(c, report, RectF(100f, 245f, 740f, 535f))
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun header(c: Canvas, report: ReportData, page: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = teal
        p.textSize = 22f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("GMES", 18f, 28f, p)
        p.color = Color.BLACK
        p.textSize = 11f
        p.textAlign = Paint.Align.CENTER
        c.drawText("Group Management Electrical Surveys", PAGE_W / 2, 18f, p)
        p.textSize = 12f
        c.drawText("Site Visit Report - ${report.projectName.ifBlank { "New Report" }}", PAGE_W / 2, 32f, p)
        p.textAlign = Paint.Align.RIGHT
        p.textSize = 8f
        c.drawText("Page $page of 3", PAGE_W - 18f, 29f, p)
        p.strokeWidth = 1.5f
        p.color = teal
        c.drawLine(18f, 37f, PAGE_W - 18f, 37f, p)
    }

    private fun details(c: Canvas, report: ReportData, top: Float) {
        val left = 18f
        val right = 680f
        val scoreLeft = 684f
        val bottom = 108f
        outline(c, RectF(left, top, right, bottom))
        val rowH = (bottom - top) / 4f
        val labelW = 155f
        val mid = 390f
        repeat(3) { i -> c.drawLine(left, top + rowH * (i + 1), right, top + rowH * (i + 1), linePaint()) }
        c.drawLine(left + labelW, top, left + labelW, bottom, linePaint())
        c.drawLine(mid, top, mid, bottom, linePaint())
        c.drawLine(mid + 115f, top, mid + 115f, bottom, linePaint())
        val labels = listOf("Client:", "Project Name:", "Person Completing Report & Role:", "GMES Site Manager/Supervisor & Role:")
        val values = listOf(
            report.client,
            report.projectName,
            listOf(report.personName, report.personRole).filter { it.isNotBlank() }.joinToString(" - "),
            listOf(report.managerName, report.managerRole).filter { it.isNotBlank() }.joinToString(" - ")
        )
        val labels2 = listOf("Site Visit Date:", "GMES Project No:", "Sector:", "Territory:")
        val values2 = listOf(displayDate(report.visitDate), report.projectNo, report.sector, report.territory)
        for (i in 0..3) {
            fill(c, RectF(left, top + i * rowH, left + labelW, top + (i + 1) * rowH), teal)
            text(c, labels[i], left + 5f, top + i * rowH + 11f, 7f, Color.WHITE, bold = true)
            text(c, values[i], left + labelW + 5f, top + i * rowH + 11f, 7f)
            fill(c, RectF(mid, top + i * rowH, mid + 115f, top + (i + 1) * rowH), teal)
            text(c, labels2[i], mid + 5f, top + i * rowH + 11f, 7f, Color.WHITE, bold = true)
            text(c, values2[i], mid + 120f, top + i * rowH + 11f, 7f)
        }
        outline(c, RectF(scoreLeft, top, PAGE_W - 18f, bottom))
        text(c, "Overall Score", (scoreLeft + PAGE_W - 18f) / 2, top + 12f, 8f, bold = true, align = Paint.Align.CENTER)
        donut(c, (scoreLeft + PAGE_W - 18f) / 2, top + 42f, 25f, report.overallPercent())
    }

    private fun drawSection(c: Canvas, report: ReportData, section: SectionDef, top: Float, bottom: Float, includeReviewer: Boolean) {
        val left = 18f
        val right = PAGE_W - 18f
        val headerH = 16f
        val commentH = if (section.id == "client") 48f else 32f
        val reviewerH = if (includeReviewer) 25f else 0f
        val questionBottom = bottom - commentH - reviewerH
        val rowH = max(10f, (questionBottom - top - headerH) / section.questions.size)
        val itemX = left + 35f
        val awardX = if (section.scored) right - 210f else right - 160f
        val availX = if (section.scored) right - 115f else right
        val ratingX = if (section.scored) right - 60f else right

        outline(c, RectF(left, top, right, bottom))
        fill(c, RectF(left, top, itemX, top + headerH), lightGray)
        fill(c, RectF(itemX, top, awardX, top + headerH), if (section.id == "operational" || section.id == "client") teal else dark)
        fill(c, RectF(awardX, top, if (section.scored) availX else right, top + headerH), lightGray)
        if (section.scored) fill(c, RectF(availX, top, ratingX, top + headerH), lightGray)

        text(c, "Item", left + 4f, top + 11f, 7f, bold = true)
        text(c, section.title, (itemX + awardX) / 2, top + 11f, 7f, if (section.id == "operational" || section.id == "client") Color.WHITE else Color.WHITE, bold = true, align = Paint.Align.CENTER)
        text(c, "Award", awardX + 4f, top + 11f, 7f, bold = true)
        if (section.scored) {
            text(c, "Available Score", availX + 3f, top + 11f, 6.5f, bold = true)
            text(c, "Rating", ratingX + 4f, top + 11f, 7f, bold = true)
        }

        var y = top + headerH
        section.questions.forEach { q ->
            val next = y + rowH
            c.drawLine(left, next, right, next, linePaint())
            c.drawLine(itemX, y, itemX, next, linePaint())
            c.drawLine(awardX, y, awardX, next, linePaint())
            if (section.scored) {
                c.drawLine(availX, y, availX, next, linePaint())
                c.drawLine(ratingX, y, ratingX, next, linePaint())
            }
            text(c, q.id, left + 4f, y + 8f, 6.5f)
            wrappedText(c, q.text, itemX + 4f, y + 3f, awardX - itemX - 8f, rowH - 4f, 5.6f)
            text(c, report.answers[q.id].orEmpty(), awardX + 4f, y + 8f, 6.5f)
            if (section.scored) text(c, "${((q.weight ?: 0.0) * 100).toInt()}%", availX + 8f, y + 8f, 6.5f)
            y = next
        }

        if (section.scored) {
            val pct = report.sectionPercent(section)
            fill(c, RectF(ratingX, top + headerH, right, questionBottom), if (pct > 80) Color.rgb(110, 225, 0) else Color.YELLOW)
            text(c, "$pct%", (ratingX + right) / 2, (top + headerH + questionBottom) / 2, 9f, bold = true, align = Paint.Align.CENTER)
        }

        val commentLabel = when (section.id) {
            "operational" -> "Comments on Operation items:"
            "client" -> "Comments on Client items:"
            else -> "Comments on ${section.title.substringAfter("- ")}:"
        }
        val commentTop = questionBottom
        c.drawLine(left, commentTop, right, commentTop, linePaint())
        text(c, commentLabel, left + 4f, commentTop + 9f, 6.5f, bold = true)
        wrappedText(c, report.comments[section.id].orEmpty(), left + 4f, commentTop + 12f, right - left - 8f, commentH - 12f, 6f)

        if (includeReviewer) {
            val reviewTop = bottom - reviewerH
            c.drawLine(left, reviewTop, right, reviewTop, linePaint())
            val split = left + (right - left) * 0.5f
            c.drawLine(split, reviewTop, split, bottom, linePaint())
            text(c, "Reviewer - Print Name", left + 4f, reviewTop + 8f, 6.5f, bold = true)
            text(c, report.reviewerName.ifBlank { report.personName }, left + 150f, reviewTop + 18f, 7f)
            text(c, "Reviewer - Signature", split + 4f, reviewTop + 8f, 6.5f, bold = true)
            drawSignature(c, report.signaturePath, RectF(split + 130f, reviewTop + 2f, right - 8f, bottom - 2f))
        }
    }

    private fun drawEvidenceGrid(c: Canvas, report: ReportData, ids: List<String>, top: Float, cols: Int, bottom: Float, left: Float = 82f, right: Float = 760f) {
        val gap = 12f
        val rows = (ids.size + cols - 1) / cols
        val w = (right - left - gap * (cols - 1)) / cols
        val h = (bottom - top - gap * (rows - 1)) / rows
        ids.forEachIndexed { i, id ->
            val col = i % cols
            val row = i / cols
            val rect = RectF(left + col * (w + gap), top + row * (h + gap), left + col * (w + gap) + w, top + row * (h + gap) + h)
            fill(c, RectF(rect.left, rect.top, rect.right, rect.top + 15f), dark)
            text(c, id, rect.centerX(), rect.top + 11f, 7f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
            outline(c, rect)
            val photoRect = RectF(rect.left + 3f, rect.top + 18f, rect.right - 3f, rect.bottom - 3f)
            val path = report.photos[id]
            if (!path.isNullOrBlank()) drawPhoto(c, path, photoRect)
            else {
                fill(c, photoRect, Color.rgb(248, 248, 248))
                text(c, "No photo", photoRect.centerX(), photoRect.centerY(), 7f, Color.GRAY, align = Paint.Align.CENTER)
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
        val chartTop = bounds.top + 15f
        val chartBottom = bounds.bottom - 35f
        val usable = chartBottom - chartTop
        for (pct in 0..100 step 10) {
            val y = chartBottom - usable * pct / 100f
            val p = linePaint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
            c.drawLine(bounds.left + 35f, y, bounds.right, y, p)
            text(c, "$pct%", bounds.left + 28f, y + 2f, 5.5f, Color.DKGRAY, align = Paint.Align.RIGHT)
        }
        val plotLeft = bounds.left + 55f
        val step = (bounds.right - plotLeft) / sections.size
        sections.forEachIndexed { i, (name, pct) ->
            val x = plotLeft + i * step + step * 0.2f
            val barW = step * 0.6f
            val filledH = usable * pct / 100f
            val emptyH = usable - filledH
            fill(c, RectF(x, chartTop, x + barW, chartTop + emptyH), Color.rgb(225, 225, 225))
            fill(c, RectF(x, chartTop + emptyH, x + barW, chartBottom), teal)
            outline(c, RectF(x, chartTop, x + barW, chartBottom))
            wrappedText(c, name, x - step * 0.1f, chartBottom + 8f, step * 0.8f, 22f, 5.5f, Paint.Align.CENTER)
        }
    }

    private fun donut(c: Canvas, cx: Float, cy: Float, radius: Float, percent: Int) {
        val stroke = 10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.LTGRAY }
        c.drawCircle(cx, cy, radius, p)
        p.color = teal
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, percent * 3.6f, false, p)
        text(c, "$percent%", cx, cy + 4f, 9f, bold = true, align = Paint.Align.CENTER)
    }

    private fun drawPhoto(c: Canvas, path: String, dest: RectF) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = loadOrientedBitmap(file) ?: return
        val srcRatio = bitmap.width.toFloat() / bitmap.height
        val dstRatio = dest.width() / dest.height()
        val src = if (srcRatio > dstRatio) {
            val newW = (bitmap.height * dstRatio).toInt()
            val x = (bitmap.width - newW) / 2
            android.graphics.Rect(x, 0, x + newW, bitmap.height)
        } else {
            val newH = (bitmap.width / dstRatio).toInt()
            val y = (bitmap.height - newH) / 2
            android.graphics.Rect(0, y, bitmap.width, y + newH)
        }
        c.drawBitmap(bitmap, src, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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
        val scale = min(dest.width() / bmp.width, dest.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val target = RectF(dest.left + (dest.width() - w) / 2, dest.top + (dest.height() - h) / 2, dest.left + (dest.width() - w) / 2 + w, dest.top + (dest.height() - h) / 2 + h)
        c.drawBitmap(bmp, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
    }

    private fun footer(c: Canvas, code: String) {
        text(c, code, 18f, PAGE_H - 8f, 5.5f, Color.DKGRAY)
    }

    private fun fill(c: Canvas, r: RectF, color: Int) {
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color })
    }

    private fun outline(c: Canvas, r: RectF) {
        c.drawRect(r, linePaint())
    }

    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 90)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = Color.BLACK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        c.drawText(value, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        })
    }

    private fun wrappedText(c: Canvas, value: String, x: Float, y: Float, width: Float, height: Float, size: Float, align: Paint.Align = Paint.Align.LEFT) {
        if (value.isBlank()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = Color.BLACK; textAlign = align }
        val words = value.replace("\n", " \n ").split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            if (word == "\n") {
                lines += line.trim(); line = ""
            } else {
                val candidate = if (line.isBlank()) word else "$line $word"
                if (p.measureText(candidate) > width && line.isNotBlank()) {
                    lines += line
                    line = word
                } else line = candidate
            }
        }
        if (line.isNotBlank()) lines += line
        val lineH = size + 1.4f
        val maxLines = max(1, (height / lineH).toInt())
        lines.take(maxLines).forEachIndexed { i, s ->
            val xx = when (align) {
                Paint.Align.CENTER -> x + width / 2
                Paint.Align.RIGHT -> x + width
                else -> x
            }
            c.drawText(s, xx, y + size + i * lineH, p)
        }
    }

    private fun displayDate(iso: String): String = try {
        val p = iso.split("-")
        "${p[2]}/${p[1]}/${p[0]}"
    } catch (_: Exception) { iso }
}
