package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Macro-free XLSX export that keeps the FM_038 key cell positions for combined-report import. */
object ExcelExporter {
    private data class Cell(val ref: String, val value: String, val style: Int = 3, val number: Boolean = false)
    private data class Box(val c1: Int, val r1: Int, val c2: Int, val r2: Int)
    private data class ImagePart(val name: String, val bytes: ByteArray, val ext: String, val box: Box)

    fun create(context: Context, report: ReportData): ByteArray {
        val images = buildImages(context, report)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }

            put("[Content_Types].xml", contentTypes(images))
            put("_rels/.rels", rootRels)
            put("xl/workbook.xml", workbook(report))
            put("xl/_rels/workbook.xml.rels", workbookRels)
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheet(report, images.isNotEmpty()))

            if (images.isNotEmpty()) {
                put("xl/worksheets/_rels/sheet1.xml.rels", sheetRels)
                put("xl/drawings/drawing1.xml", drawing(images))
                put("xl/drawings/_rels/drawing1.xml.rels", drawingRels(images))
                images.forEachIndexed { i, image ->
                    zip.putNextEntry(ZipEntry("xl/media/image${i + 1}.${image.ext}"))
                    zip.write(image.bytes)
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun buildImages(context: Context, report: ReportData): List<ImagePart> {
        val result = mutableListOf<ImagePart>()
        val gmes = BrandAssets.gmes(context)?.let(::pngBytes)
        val smart = BrandAssets.thinkSmart(context)?.let(::pngBytes)

        // Repeat the supplied branding at the top of each of the three printed pages.
        listOf(0, 90, 182).forEachIndexed { page, row ->
            gmes?.let { result += ImagePart("GMES logo page ${page + 1}", it, "png", Box(1, row, 3, row + 3)) }
            smart?.let { result += ImagePart("Think SMART page ${page + 1}", it, "png", Box(9, row, 12, row + 3)) }
        }

        report.photos.toSortedMap().forEach { (id, path) ->
            val file = File(path)
            if (file.exists() && id in setOf("1.01", "1.02", "1.03", "1.04", "1.05", "1.06", "2.02", "2.03")) {
                result += ImagePart("Evidence $id", file.readBytes(), ext(file), evidenceBox(id))
            }
        }

        val signature = File(report.signaturePath)
        if (report.signaturePath.isNotBlank() && signature.exists()) {
            val bytes = signature.readBytes()
            val ext = ext(signature)
            val signatureBoxes = listOf(
                Box(5, 20, 8, 24),   // Policy
                Box(5, 107, 8, 111), // PPE
                Box(5, 157, 8, 161), // Equipment
                Box(5, 178, 8, 181), // Operational
                Box(5, 197, 9, 201)  // Client
            )
            signatureBoxes.forEachIndexed { i, box ->
                result += ImagePart("Reviewer signature ${i + 1}", bytes, ext, box)
            }
        }
        return result
    }

    private fun pngBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    private fun contentTypes(images: List<ImagePart>) = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
${if (images.any { it.ext == "jpg" }) "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" else ""}
${if (images.any { it.ext == "png" }) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""}
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
${if (images.isNotEmpty()) "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>" else ""}
</Types>"""

    private val rootRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(report: ReportData): String {
        val name = safeSheetName(report.projectName.ifBlank { "New Report" })
        val dollar = '$'
        return """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<bookViews><workbookView xWindow="0" yWindow="0" windowWidth="24000" windowHeight="12000"/></bookViews>
<sheets><sheet name="${xml(name)}" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'${xml(name.replace("'", "''"))}'!${dollar}B${dollar}1:${dollar}L${dollar}242</definedName></definedNames>
</workbook>"""
    }

    private val workbookRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="5">
<font><sz val="8"/><name val="Arial"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="8"/><name val="Arial"/></font>
<font><b/><sz val="8"/><name val="Arial"/></font>
<font><b/><sz val="11"/><name val="Arial"/></font>
<font><sz val="7"/><name val="Arial"/></font>
</fonts>
<fills count="7">
<fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0C6E6A"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF7CDD12"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFE300"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/></patternFill></fill>
</fills>
<borders count="2"><border/><border><left style="thin"><color rgb="FF666666"/></left><right style="thin"><color rgb="FF666666"/></right><top style="thin"><color rgb="FF666666"/></top><bottom style="thin"><color rgb="FF666666"/></bottom></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="11">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="6" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="10" fontId="2" fillId="6" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="2" fillId="4" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="2" fillId="5" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun sheet(report: ReportData, hasDrawing: Boolean): String {
        val cells = mutableListOf<Cell>()
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        fun n(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }

        // Page 1 heading and details. Key report data positions are intentionally unchanged.
        t("E1", "Group Management Electrical Surveys", 6)
        t("H1", "Site Visit Report - ${report.projectName.ifBlank { "New Report" }}", 6)
        t("L1", "Page 1 of 3", 10)
        t("C2", "Client:", 1); t("E2", report.client)
        t("F2", "Site Visit Date:", 1); t("G2", display(report.visitDate))
        t("C3", "Project Name:", 1); t("E3", report.projectName)
        t("F3", "GMES Project No:", 1); t("G3", report.projectNo)
        t("C4", "Person Completing Report & Role:", 1); t("E4", join(report.personName, report.personRole))
        t("F4", "Sector:", 1); t("G4", report.sector)
        t("C5", "GMES Site Manager/Supervisor & Role:", 1); t("E5", join(report.managerName, report.managerRole))
        t("F5", "Territory:", 1); t("G5", report.territory)
        n("J3", report.overallPercent() / 100.0, if (report.overallPercent() > 80) 7 else 8)
        t("K3", "Overall Score", 9)

        scored(cells, report, ReportTemplate.section("policy"), 8, 10, 17, 21, "J10", "K10")

        // Page 2 heading.
        t("E91", "Group Management Electrical Surveys", 6)
        t("H91", "Site Visit Report - ${report.projectName.ifBlank { "New Report" }}", 6)
        t("L91", "Page 2 of 3", 10)
        scored(cells, report, ReportTemplate.section("ppe"), 93, 95, 104, 108, "J95", "K95")
        scored(cells, report, ReportTemplate.section("equipment"), 148, 150, 154, 158, "J150", "K150")
        scored(cells, report, ReportTemplate.section("operational"), 164, 166, 175, 179, "J166", "K166")

        // Page 3 heading/client section.
        t("E183", "Group Management Electrical Surveys", 6)
        t("H183", "Site Visit Report - ${report.projectName.ifBlank { "New Report" }}", 6)
        t("L183", "Page 3 of 3", 10)
        client(cells, report)

        // Hidden machine-readable summary retained for combined-report use.
        val scoredSections = ReportTemplate.sections.filter { it.scored }
        val summary = listOf("Overall Score" to report.overallPercent()) + scoredSections.map { it.title.substringAfter("- ").trim() to report.sectionPercent(it) }
        t("U18", "Section", 2); t("V18", "Completed %", 2); t("W18", "Missing %", 2)
        summary.forEachIndexed { i, item ->
            val row = 19 + i
            t("U$row", item.first)
            n("V$row", item.second / 100.0)
            n("W$row", 1 - item.second / 100.0)
        }

        val customHeights = mapOf(
            1 to 24.0, 2 to 23.0, 3 to 23.0, 4 to 29.0, 5 to 29.0,
            8 to 22.0, 17 to 18.0, 18 to 34.0, 21 to 19.0, 22 to 26.0,
            91 to 24.0, 93 to 22.0, 104 to 18.0, 105 to 32.0, 108 to 19.0, 109 to 25.0,
            148 to 22.0, 154 to 18.0, 155 to 30.0, 158 to 19.0, 159 to 24.0,
            164 to 22.0, 175 to 18.0, 176 to 34.0, 179 to 19.0, 180 to 24.0,
            183 to 24.0, 185 to 22.0, 194 to 18.0, 195 to 40.0, 198 to 19.0, 199 to 26.0
        )

        val rows = cells.groupBy { it.ref.filter(Char::isDigit).toInt() }.toSortedMap()
        val allRows = (rows.keys + customHeights.keys).toSortedSet()
        val body = buildString {
            append("<sheetData>")
            allRows.forEach { row ->
                val height = customHeights[row]
                append("<row r=\"$row\"")
                if (height != null) append(" ht=\"$height\" customHeight=\"1\"")
                append(">")
                rows[row].orEmpty().sortedBy { col(it.ref) }.forEach { c ->
                    if (c.number) append("<c r=\"${c.ref}\" s=\"${c.style}\"><v>${c.value}</v></c>")
                    else append("<c r=\"${c.ref}\" s=\"${c.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(c.value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData>")
        }

        val merges = listOf(
            "E1:G1", "H1:J1", "E91:G91", "H91:J91", "E183:G183", "H183:J183",
            "D8:G8", "J10:J15", "K10:K15", "C17:K17", "C18:K20", "C21:D21", "E21:H21",
            "D93:G93", "J95:J102", "K95:K102", "C104:K104", "C105:K107", "C108:D108", "E108:H108",
            "D148:G148", "J150:J152", "K150:K152", "C154:K154", "C155:K157", "C158:D158", "E158:H158",
            "D164:G164", "J166:J173", "K166:K173", "C175:K175", "C176:K178", "C179:D179", "E179:H179",
            "D185:G185", "C194:K194", "C195:K197", "C198:E198", "F198:I198"
        )
        val mergeXml = "<mergeCells count=\"${merges.size}\">${merges.joinToString("") { "<mergeCell ref=\"$it\"/>" }}</mergeCells>"
        val drawing = if (hasDrawing) "<drawing r:id=\"rId1\"/>" else ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>
<dimension ref="B1:W242"/>
<sheetViews><sheetView workbookViewId="0" showGridLines="0"><selection activeCell="E2" sqref="E2"/></sheetView></sheetViews>
<sheetFormatPr defaultRowHeight="12.5"/>
<cols>
<col min="2" max="2" width="2.2" customWidth="1"/>
<col min="3" max="3" width="7.5" customWidth="1"/>
<col min="4" max="4" width="44" customWidth="1"/>
<col min="5" max="5" width="18" customWidth="1"/>
<col min="6" max="7" width="15" customWidth="1"/>
<col min="8" max="8" width="14" customWidth="1"/>
<col min="9" max="9" width="12" customWidth="1"/>
<col min="10" max="10" width="11" customWidth="1"/>
<col min="11" max="11" width="14" customWidth="1"/>
<col min="12" max="12" width="10" customWidth="1"/>
<col min="14" max="23" hidden="1" width="9" customWidth="1"/>
</cols>
$body
$mergeXml
<printOptions horizontalCentered="1" verticalCentered="0"/>
<pageMargins left="0.2" right="0.2" top="0.25" bottom="0.25" header="0.1" footer="0.1"/>
<pageSetup paperSize="9" orientation="portrait" pageOrder="downThenOver" fitToWidth="1" fitToHeight="3"/>
<rowBreaks count="2" manualBreakCount="2"><brk id="90" min="0" max="16383" man="1"/><brk id="182" min="0" max="16383" man="1"/></rowBreaks>
$drawing
</worksheet>"""
    }

    private fun scored(
        cells: MutableList<Cell>,
        report: ReportData,
        section: SectionDef,
        header: Int,
        first: Int,
        comments: Int,
        reviewer: Int,
        scoreCell: String,
        ratingCell: String
    ) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        fun n(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }

        t("C$header", "Item", 2)
        t("D$header", section.title + if (section.subtitle.isBlank()) "" else "\n(${section.subtitle})", 1)
        t("H$header", "Award", 2)
        t("I$header", "Available Score", 2)
        t("J$header", "Score", 2)
        t("K$header", "Rating", 2)

        section.questions.forEachIndexed { i, q ->
            val row = first + i
            t("C$row", q.id)
            t("D$row", q.text)
            t("H$row", report.answers[q.id].orEmpty())
            n("I$row", q.weight ?: 0.0)
        }

        val pct = report.sectionPercent(section) / 100.0
        n(scoreCell, pct, if (pct > .8) 7 else 8)
        t(ratingCell, if (pct > .8) "Acceptable" else "To Improve", 9)
        t("C$comments", when (section.id) {
            "operational" -> "Comments on Operation items:"
            else -> "Comments on ${section.title.substringAfter("- ")}:"
        }, 2)
        t("C${comments + 1}", report.comments[section.id].orEmpty())
        t("C$reviewer", "Reviewer - Print Name", 2)
        t("E$reviewer", "Reviewer - Signature", 2)
        t("C${reviewer + 1}", reviewerDisplay(report))
    }

    private fun client(cells: MutableList<Cell>, report: ReportData) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        val section = ReportTemplate.section("client")
        t("C185", "Item", 2)
        t("D185", section.title, 1)
        t("H185", "Award", 2)
        section.questions.forEachIndexed { i, q ->
            val row = 187 + i
            t("C$row", q.id)
            t("D$row", q.text)
            t("H$row", report.answers[q.id].orEmpty())
        }
        t("C194", "Comments on Client items:", 2)
        t("C195", report.comments[section.id].orEmpty())
        t("C198", "Reviewer - Print Name", 2)
        t("F198", "Reviewer - Signature", 2)
        t("C199", reviewerDisplay(report))
    }

    private val sheetRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>"""

    private fun evidenceBox(id: String) = when (id) {
        "1.01" -> Box(2, 27, 5, 55)
        "1.02" -> Box(5, 27, 8, 55)
        "1.03" -> Box(8, 27, 11, 55)
        "1.04" -> Box(2, 57, 5, 88)
        "1.05" -> Box(5, 57, 8, 88)
        "1.06" -> Box(8, 57, 11, 88)
        "2.02" -> Box(3, 115, 6, 145)
        "2.03" -> Box(6, 115, 9, 145)
        else -> Box(3, 205, 9, 213)
    }

    private fun drawing(images: List<ImagePart>) = """<?xml version="1.0" encoding="UTF-8"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
${images.mapIndexed { i, image ->
        val b = image.box
        """<xdr:twoCellAnchor editAs="oneCell">
<xdr:from><xdr:col>${b.c1}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r1}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>
<xdr:to><xdr:col>${b.c2}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r2}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>
<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${i + 2}" name="${xml(image.name)}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>
<xdr:blipFill><a:blip r:embed="rId${i + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>
<xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/>
</xdr:twoCellAnchor>"""
    }.joinToString("\n")}
</xdr:wsDr>"""

    private fun drawingRels(images: List<ImagePart>) = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
${images.mapIndexed { i, image -> "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${i + 1}.${image.ext}\"/>" }.joinToString("\n")}
</Relationships>"""

    private fun ext(file: File): String = if (file.extension.lowercase() == "png") "png" else "jpg"
    private fun safeSheetName(value: String) = value.replace(Regex("[\\\\/:*?\\[\\]]"), "-").trim().ifBlank { "New Report" }.take(31)
    private fun join(a: String, b: String) = listOf(a, b).filter(String::isNotBlank).joinToString(" - ")
    private fun display(value: String) = try { val p = value.split("-"); "${p[2]}/${p[1]}/${p[0]}" } catch (_: Exception) { value }
    private fun reviewerDisplay(report: ReportData): String {
        val name = report.reviewerName.ifBlank { report.personName }.trim()
        val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts.first().firstOrNull()?.uppercaseChar() ?: ' '}.${parts.last()}" else name
    }
    private fun col(ref: String): Int { var n = 0; ref.takeWhile(Char::isLetter).forEach { n = n * 26 + (it.uppercaseChar() - 'A' + 1) }; return n }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
