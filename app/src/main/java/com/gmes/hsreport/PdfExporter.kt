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
    private const val PAGE_W = 842f // A4 landscape points
    private const val PAGE_H = 595f
    private val teal = Color.rgb(12, 110, 106)
    private val dark = Color.rgb(80, 80, 80)
    private val lightGray = Color.rgb(242, 242, 242)
    private val improveRed = Color.rgb(242, 55, 55)
    private val acceptableGreen = Color.rgb(124, 221, 18)

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
        details(c, report, 44f)
        drawSection(c, report, ReportTemplate.section("policy"), 118f, 252f)
        drawEvidenceGrid(
            c,
            report,
            listOf("1.01", "1.02", "1.03", "1.04", "1.05", "1.06"),
            top = 304f,
            cols = 3,
            bottom = 568f
        )
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage2(pdf: PdfDocument, context: Context, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 2).create())
        val c = page.canvas
        header(c, context, report, 2)
        drawSection(c, report, ReportTemplate.section("ppe"), 44f, 176f)
        drawEvidenceGrid(
            c,
            report,
            listOf("2.02", "2.03"),
            top = 184f,
            cols = 2,
            bottom = 310f,
            left = 190f,
            right = 650f
        )
        drawSection(c, report, ReportTemplate.section("equipment"), 320f, 414f)
        drawSection(c, report, ReportTemplate.section("operational"), 422f, 559f)
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun drawPage3(pdf: PdfDocument, context: Context, report: ReportData) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 3).create())
        val c = page.canvas
        header(c, context, report, 3)
        drawSection(c, report, ReportTemplate.section("client"), 44f, 220f)
        drawScoreChart(c, report, RectF(100f, 250f, 740f, 535f))
        footer(c, "FM_038-12")
        pdf.finishPage(page)
    }

    private fun header(c: Canvas, context: Context, report: ReportData, page: Int) {
        drawBitmapFit(c, BrandAssets.gmes(context), RectF(18f, 4f, 132f, 35f))
        drawBitmapFit(c, BrandAssets.thinkSmart(context), RectF(PAGE_W - 170f, 6f, PAGE_W - 18f, 31f))

        val titleLeft = 145f
        val titleRight = PAGE_W - 185f
        fittedText(
            c,
            "Group Management Electrical Surveys",
            (titleLeft + titleRight) / 2f,
            15f,
            titleRight - titleLeft,
            10.5f,
            7.5f,
            bold = true,
            align = Paint.Align.CENTER
        )
        fittedText(
            c,
            "Site Visit Report - ${report.projectName.ifBlank { "New Report" }}",
            (titleLeft + titleRight) / 2f,
            30f,
            titleRight - titleLeft,
            11.5f,
            7.5f,
            align = Paint.Align.CENTER
        )
        text(c, "Page $page of 3", PAGE_W - 18f, 38f, 6.5f, Color.DKGRAY, align = Paint.Align.RIGHT)

        val p = linePaint().apply {
            color = teal
            strokeWidth = 1.4f
        }
        c.drawLine(18f, 40f, PAGE_W - 18f, 40f, p)
    }

    private fun details(c: Canvas, report: ReportData, top: Float) {
        val left = 18f
        val right = 680f
        val scoreLeft = 684f
        val bottom = 111f
        outline(c, RectF(left, top, right, bottom))
        val rowH = (bottom - top) / 4f
        val labelW = 155f
        val mid = 390f
        repeat(3) { i ->
            c.drawLine(left, top + rowH * (i + 1), right, top + rowH * (i + 1), linePaint())
        }
        c.drawLine(left + labelW, top, left + labelW, bottom, linePaint())
        c.drawLine(mid, top, mid, bottom, linePaint())
        c.drawLine(mid + 115f, top, mid + 115f, bottom, linePaint())

        val labels = listOf(
            "Client:",
            "Project Name:",
            "Person Completing Report & Role:",
            "GMES Site Manager/Supervisor & Role:"
        )
        val values = listOf(
            report.client,
            report.projectName,
            listOf(report.personName, report.personRole).filter { it.isNotBlank() }.joinToString(" - "),
            listOf(report.managerName, report.managerRole).filter { it.isNotBlank() }.joinToString(" - ")
        )
        val labels2 = listOf("Site Visit Date:", "GMES Project No:", "Sector:", "Territory:")
        val values2 = listOf(displayDate(report.visitDate), report.projectNo, report.sector, report.territory)

        for (i in 0..3) {
            val y0 = top + i * rowH
            val yText = y0 + rowH * 0.68f
            fill(c, RectF(left, y0, left + labelW, y0 + rowH), teal)
            fittedText(c, labels[i], left + 5f, yText, labelW - 10f, 6.8f, 5f, Color.WHITE, bold = true)
            fittedText(c, values[i], left + labelW + 5f, yText, mid - (left + labelW) - 10f, 6.8f, 4.5f)

            fill(c, RectF(mid, y0, mid + 115f, y0 + rowH), teal)
            fittedText(c, labels2[i], mid + 5f, yText, 105f, 6.8f, 5f, Color.WHITE, bold = true)
            fittedText(c, values2[i], mid + 120f, yText, right - (mid + 120f) - 5f, 6.8f, 4.5f)
        }

        outline(c, RectF(scoreLeft, top, PAGE_W - 18f, bottom))
        text(
            c,
            "Overall Score",
            (scoreLeft + PAGE_W - 18f) / 2f,
            top + 13f,
            7.5f,
            bold = true,
            align = Paint.Align.CENTER
        )
        donut(c, (scoreLeft + PAGE_W - 18f) / 2f, top + 44f, 25f, report.overallPercent())
    }

    private fun drawSection(c: Canvas, report: ReportData, section: SectionDef, top: Float, bottom: Float) {
        val left = 18f
        val right = PAGE_W - 18f
        val headerH = 14f
        val commentH = if (section.id == "client") 52f else 26f
        val reviewerH = 22f
        val questionBottom = bottom - commentH - reviewerH
        val questionSpace = questionBottom - top - headerH
        val rowH = questionSpace / section.questions.size.toFloat()
        val itemX = left + 35f

        val awardX: Float
        val availableX: Float
        val scoreX: Float
        val ratingX: Float
        if (section.scored) {
            awardX = right - 235f
            availableX = right - 145f
            scoreX = right - 85f
            ratingX = right - 45f
        } else {
            awardX = right - 120f
            availableX = right
            scoreX = right
            ratingX = right
        }

        outline(c, RectF(left, top, right, bottom))
        fill(c, RectF(left, top, itemX, top + headerH), lightGray)
        fill(c, RectF(itemX, top, awardX, top + headerH), teal)
        fill(c, RectF(awardX, top, if (section.scored) availableX else right, top + headerH), lightGray)
        if (section.scored) {
            fill(c, RectF(availableX, top, scoreX, top + headerH), lightGray)
            fill(c, RectF(scoreX, top, ratingX, top + headerH), lightGray)
            fill(c, RectF(ratingX, top, right, top + headerH), lightGray)
        }

        text(c, "Item", left + 4f, top + 10f, 6.5f, bold = true)
        fittedText(
            c,
            section.title,
            (itemX + awardX) / 2f,
            top + 10f,
            awardX - itemX - 8f,
            6.6f,
            4.8f,
            Color.WHITE,
            bold = true,
            align = Paint.Align.CENTER
        )
        text(c, "Award", awardX + 4f, top + 10f, 6.2f, bold = true)
        if (section.scored) {
            fittedText(c, "Available Score", (availableX + scoreX) / 2f, top + 10f, scoreX - availableX - 4f, 6f, 4.5f, bold = true, align = Paint.Align.CENTER)
            text(c, "Score", (scoreX + ratingX) / 2f, top + 10f, 6f, bold = true, align = Paint.Align.CENTER)
            text(c, "Rating", (ratingX + right) / 2f, top + 10f, 5.8f, bold = true, align = Paint.Align.CENTER)
        }

        var y = top + headerH
        section.questions.forEach { q ->
            val next = y + rowH
            c.drawLine(left, next, if (section.scored) scoreX else right, next, linePaint())
            c.drawLine(itemX, y, itemX, next, linePaint())
            c.drawLine(awardX, y, awardX, next, linePaint())
            if (section.scored) {
                c.drawLine(availableX, y, availableX, next, linePaint())
                c.drawLine(scoreX, y, scoreX, next, linePaint())
            }

            fittedText(c, q.id, left + 4f, y + rowH * 0.68f, itemX - left - 7f, 6f, 4.2f)
            wrappedTextAuto(
                c,
                q.text,
                itemX + 4f,
                y + 1f,
                awardX - itemX - 8f,
                rowH - 2f,
                5.6f,
                3.8f
            )
            fittedText(c, report.answers[q.id].orEmpty(), awardX + 4f, y + rowH * 0.68f, if (section.scored) availableX - awardX - 8f else right - awardX - 8f, 6f, 4f)
            if (section.scored) {
                val weightPct = ((q.weight ?: 0.0) * 100.0).toInt()
                fittedText(c, "$weightPct%", (availableX + scoreX) / 2f, y + rowH * 0.68f, scoreX - availableX - 4f, 5.8f, 4f, align = Paint.Align.CENTER)
            }
            y = next
        }

        if (section.scored) {
            val pct = report.sectionPercent(section)
            val ratingColor = if (pct > 80) acceptableGreen else improveRed
            fill(c, RectF(scoreX, top + headerH, ratingX, questionBottom), ratingColor)
            fill(c, RectF(ratingX, top + headerH, right, questionBottom), Color.WHITE)
            outline(c, RectF(scoreX, top + headerH, ratingX, questionBottom))
            outline(c, RectF(ratingX, top + headerH, right, questionBottom))
            text(c, "$pct%", (scoreX + ratingX) / 2f, (top + headerH + questionBottom) / 2f + 3f, 7.5f, bold = true, align = Paint.Align.CENTER)
            wrappedTextAuto(
                c,
                if (pct > 80) "Acceptable" else "To Improve",
                ratingX + 2f,
                (top + headerH + questionBottom) / 2f - 7f,
                right - ratingX - 4f,
                18f,
                5.2f,
                3.8f,
                Paint.Align.CENTER
            )
        }

        val commentLabel = when (section.id) {
            "operational" -> "Comments on Operation items:"
            "client" -> "Comments on Client items:"
            else -> "Comments on ${section.title.substringAfter("- ")}:"
        }
        val commentTop = questionBottom
        c.drawLine(left, commentTop, right, commentTop, linePaint())
        fill(c, RectF(left, commentTop, right, commentTop + 11f), Color.rgb(255, 252, 182))
        text(c, commentLabel, left + 4f, commentTop + 8f, 6f, bold = true)
        wrappedTextAuto(
            c,
            report.comments[section.id].orEmpty(),
            left + 4f,
            commentTop + 11f,
            right - left - 8f,
            commentH - 12f,
            5.8f,
            3.1f
        )

        val reviewTop = bottom - reviewerH
        c.drawLine(left, reviewTop, right, reviewTop, linePaint())
        val split = left + (right - left) * 0.5f
        c.drawLine(split, reviewTop, split, bottom, linePaint())
        text(c, "Reviewer - Print Name", left + 4f, reviewTop + 7f, 5.8f, bold = true)
        fittedText(
            c,
            report.reviewerName.ifBlank { report.personName },
            left + 140f,
            reviewTop + 16f,
            split - left - 148f,
            6.4f,
            4.2f
        )
        text(c, "Reviewer - Signature", split + 4f, reviewTop + 7f, 5.8f, bold = true)
        drawSignature(c, report.signaturePath, RectF(split + 120f, reviewTop + 2f, right - 8f, bottom - 2f))
    }

    private fun drawEvidenceGrid(
        c: Canvas,
        report: ReportData,
        ids: List<String>,
        top: Float,
        cols: Int,
        bottom: Float,
        left: Float = 82f,
        right: Float = 760f
    ) {
        val gap = 12f
        val rows = (ids.size + cols - 1) / cols
        val w = (right - left - gap * (cols - 1)) / cols
        val h = (bottom - top - gap * (rows - 1)) / rows
        ids.forEachIndexed { i, id ->
            val col = i % cols
            val row = i / cols
            val rect = RectF(
                left + col * (w + gap),
                top + row * (h + gap),
                left + col * (w + gap) + w,
                top + row * (h + gap) + h
            )
            fill(c, RectF(rect.left, rect.top, rect.right, rect.top + 15f), dark)
            text(c, id, rect.centerX(), rect.top + 11f, 7f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
            outline(c, rect)
            val photoRect = RectF(rect.left + 3f, rect.top + 18f, rect.right - 3f, rect.bottom - 3f)
            val path = report.photos[id]
            if (!path.isNullOrBlank()) {
                drawPhoto(c, path, photoRect)
            } else {
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
            val p = linePaint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.5f
            }
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
            wrappedTextAuto(
                c,
                name,
                x - step * 0.1f,
                chartBottom + 7f,
                step * 0.8f,
                24f,
                5.4f,
                4.1f,
                Paint.Align.CENTER
            )
        }
    }

    private fun donut(c: Canvas, cx: Float, cy: Float, radius: Float, percent: Int) {
        val stroke = 10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Color.LTGRAY
        }
        c.drawCircle(cx, cy, radius, p)
        p.color = teal
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, percent * 3.6f, false, p)
        text(c, "$percent%", cx, cy + 4f, 9f, bold = true, align = Paint.Align.CENTER)
    }

    /**
     * Fits the whole photo inside its evidence box. Portrait photos stay portrait and landscape photos stay landscape;
     * nothing is stretched or centre-cropped.
     */
    private fun drawPhoto(c: Canvas, path: String, dest: RectF) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = loadOrientedBitmap(file) ?: return
        fill(c, dest, Color.WHITE)
        val scale = min(dest.width() / bitmap.width.toFloat(), dest.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val target = RectF(
            dest.left + (dest.width() - w) / 2f,
            dest.top + (dest.height() - h) / 2f,
            dest.left + (dest.width() - w) / 2f + w,
            dest.top + (dest.height() - h) / 2f + h
        )
        c.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }

    private fun loadOrientedBitmap(file: File): Bitmap? {
        var bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = try {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
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
        val target = RectF(
            dest.left + (dest.width() - w) / 2f,
            dest.top + (dest.height() - h) / 2f,
            dest.left + (dest.width() - w) / 2f + w,
            dest.top + (dest.height() - h) / 2f + h
        )
        c.drawBitmap(bmp, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
    }

    private fun drawBitmapFit(c: Canvas, bitmap: Bitmap?, dest: RectF) {
        bitmap ?: return
        val scale = min(dest.width() / bitmap.width.toFloat(), dest.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val target = RectF(
            dest.left + (dest.width() - w) / 2f,
            dest.top + (dest.height() - h) / 2f,
            dest.left + (dest.width() - w) / 2f + w,
            dest.top + (dest.height() - h) / 2f + h
        )
        c.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun footer(c: Canvas, code: String) {
        text(c, code, 18f, PAGE_H - 8f, 5.5f, Color.DKGRAY)
    }

    private fun fill(c: Canvas, r: RectF, color: Int) {
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        })
    }

    private fun outline(c: Canvas, r: RectF) {
        c.drawRect(r, linePaint())
    }

    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 90)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }

    private fun text(
        c: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = Color.BLACK,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (value.isBlank()) return
        c.drawText(value, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        })
    }

    private fun fittedText(
        c: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        preferredSize: Float,
        minSize: Float,
        color: Int = Color.BLACK,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (value.isBlank() || width <= 0f) return
        var size = preferredSize
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        while (size > minSize) {
            p.textSize = size
            if (p.measureText(value) <= width) break
            size -= 0.25f
        }
        p.textSize = size.coerceAtLeast(minSize)
        c.drawText(value, x, y, p)
    }

    /** Shrinks text until all lines fit the fixed report box, preserving the 3-page layout. */
    private fun wrappedTextAuto(
        c: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        preferredSize: Float,
        minSize: Float,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (value.isBlank() || width <= 0f || height <= 0f) return
        var size = preferredSize
        var lines: List<String>
        var lineH: Float
        while (true) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size }
            lines = layoutLines(value, p, width)
            lineH = size + 1.25f
            if (lines.size * lineH <= height || size <= minSize) break
            size = (size - 0.25f).coerceAtLeast(minSize)
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.BLACK
            textAlign = align
        }
        val maxLines = (height / lineH).toInt().coerceAtLeast(1)
        val visible = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines && visible.isNotEmpty()) {
            visible[visible.lastIndex] = visible.last().trimEnd().removeSuffix("…") + "…"
        }
        visible.forEachIndexed { i, line ->
            val xx = when (align) {
                Paint.Align.CENTER -> x + width / 2f
                Paint.Align.RIGHT -> x + width
                else -> x
            }
            c.drawText(line, xx, y + size + i * lineH, p)
        }
    }

    private fun layoutLines(value: String, paint: Paint, width: Float): List<String> {
        val result = mutableListOf<String>()
        value.replace("\r", "").split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                result += ""
            } else {
                var line = ""
                paragraph.trim().split(Regex("\\s+")).forEach { word ->
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (paint.measureText(candidate) > width && line.isNotBlank()) {
                        result += line
                        line = word
                    } else {
                        line = candidate
                    }
                }
                if (line.isNotBlank()) result += line
            }
        }
        return result
    }

    private fun displayDate(iso: String): String = try {
        val p = iso.split("-")
        "${p[2]}/${p[1]}/${p[0]}"
    } catch (_: Exception) {
        iso
    }
}
