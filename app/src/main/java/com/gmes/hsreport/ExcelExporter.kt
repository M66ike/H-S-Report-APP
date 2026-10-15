package com.gmes.hsreport

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates a macro-free .xlsx report using the same fixed cell positions used by
 * the existing FM_038 New Report sheet. This lets a completed app report be
 * copied/imported into the existing scoring workbook without VBA being needed
 * on the phone.
 */
object ExcelExporter {
    private const val TEAL = "0C6E6A"
    private const val DARK = "5A5A5A"

    fun create(context: Context, report: ReportData): ByteArray {
        val photos = report.photos.entries
            .mapNotNull { (question, path) ->
                val file = File(path)
                if (file.exists()) question to file else null
            }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("[Content_Types].xml", contentTypes(photos))
            entry("_rels/.rels", rootRels())
            entry("docProps/app.xml", appProps())
            entry("docProps/core.xml", coreProps())
            entry("xl/workbook.xml", workbook(report))
            entry("xl/_rels/workbook.xml.rels", workbookRels())
            entry("xl/styles.xml", styles())
            entry("xl/worksheets/sheet1.xml", sheet(report, photos.isNotEmpty()))

            if (photos.isNotEmpty()) {
                entry("xl/worksheets/_rels/sheet1.xml.rels", sheetRels())
                entry("xl/drawings/drawing1.xml", drawing(photos))
                entry("xl/drawings/_rels/drawing1.xml.rels", drawingRels(photos))
                photos.forEachIndexed { index, (_, file) ->
                    val ext = imageExtension(file)
                    zip.putNextEntry(ZipEntry("xl/media/image${index + 1}.$ext"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun contentTypes(photos: List<Pair<String, File>>): String {
        val imageDefaults = buildString {
            if (photos.any { imageExtension(it.second) == "jpg" }) append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
            if (photos.any { imageExtension(it.second) == "png" }) append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        }
        val drawing = if (photos.isNotEmpty()) "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
$imageDefaults
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
$drawing
</Types>"""
    }

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>GMES H&amp;S Report App</Application></Properties>"""

    private fun coreProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:creator>GMES H&amp;S Report App</dc:creator><cp:lastModifiedBy>GMES H&amp;S Report App</cp:lastModifiedBy></cp:coreProperties>"""

    private fun workbook(report: ReportData): String {
        val name = safeSheetName(report.projectName.ifBlank { "New Report" })
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${xml(name)}" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'${xml(name.replace("'", "''"))}'!$B$1:$L$242</definedName></definedNames>
</workbook>"""
    }

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4">
<font><sz val="9"/><name val="Arial"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="9"/><name val="Arial"/></font>
<font><b/><sz val="9"/><name val="Arial"/></font>
<font><b/><sz val="12"/><name val="Arial"/></font>
</fonts>
<fills count="5">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF$TEAL"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF$DARK"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border/><border><left style="thin"><color rgb="FF666666"/></left><right style="thin"><color rgb="FF666666"/></right><top style="thin"><color rgb="FF666666"/></top><bottom style="thin"><color rgb="FF666666"/></bottom></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="2" fillId="4" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="10" fontId="2" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="left" vertical="top" wrapText="1"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private data class Cell(val ref: String, val value: String, val style: Int = 3, val numeric: Boolean = false)

    private fun sheet(report: ReportData, hasDrawing: Boolean): String {
        val cells = mutableListOf<Cell>()
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        fun n(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }

        // Header - same positions as FM_038 New Report.
        t("C1", "Group Management Electrical Surveys", 6)
        t("C2", "Client:", 1); t("E2", report.client)
        t("F2", "Site Visit Date:", 1); t("G2", displayDate(report.visitDate))
        t("C3", "Project Name:", 1); t("E3", report.projectName)
        t("F3", "GMES Project No:", 1); t("G3", report.projectNo)
        t("C4", "Person Completing Report & Role:", 1); t("E4", listOf(report.personName, report.personRole).filter { it.isNotBlank() }.joinToString(" - "))
        t("F4", "Sector:", 1); t("G4", report.sector)
        t("C5", "GMES Site Manager/Supervisor & Role:", 1); t("E5", listOf(report.managerName, report.managerRole).filter { it.isNotBlank() }.joinToString(" - "))
        t("F5", "Territory:", 1); t("G5", report.territory)
        n("R3", 1.0); n("R4", report.overallPercent() / 100.0); n("R5", 1.0 - report.overallPercent() / 100.0)

        addSection(cells, report, ReportTemplate.section("policy"), headerRow = 8, firstRow = 10, commentRow = 17, reviewerRow = 21, helperRow = 13, scoreCell = "J10", ratingCell = "K10", helperColRow = 13)
        addSection(cells, report, ReportTemplate.section("ppe"), headerRow = 93, firstRow = 95, commentRow = 104, reviewerRow = 108, helperRow = 100, scoreCell = "J95", ratingCell = "K95", helperColRow = 100)
        addSection(cells, report, ReportTemplate.section("equipment"), headerRow = 148, firstRow = 150, commentRow = 154, reviewerRow = 158, helperRow = 150, scoreCell = "J150", ratingCell = "K150", helperColRow = 150)
        addSection(cells, report, ReportTemplate.section("operational"), headerRow = 164, firstRow = 166, commentRow = 175, reviewerRow = 179, helperRow = 171, scoreCell = "J166", ratingCell = "K166", helperColRow = 171)
        addClientSection(cells, report)

        // Data used by the existing summary/chart logic.
        val policy = ReportTemplate.section("policy")
        val ppe = ReportTemplate.section("ppe")
        val equipment = ReportTemplate.section("equipment")
        val operational = ReportTemplate.section("operational")
        t("U18", "Section", 2); t("V18", "Completed %", 2); t("W18", "Missing %", 2)
        listOf(
            Triple("Overall Score", report.overallPercent() / 100.0, 19),
            Triple("Policy Documents", report.sectionPercent(policy) / 100.0, 20),
            Triple("PPE/Training/Inspections", report.sectionPercent(ppe) / 100.0, 21),
            Triple("Equipment", report.sectionPercent(equipment) / 100.0, 22),
            Triple("Operational", report.sectionPercent(operational) / 100.0, 23)
        ).forEach { (name, score, row) ->
            t("U$row", name, 2); n("V$row", score); n("W$row", 1.0 - score)
        }

        // Evidence labels above the fixed photo boxes.
        listOf("1.01" to "C27", "1.02" to "F27", "1.03" to "I27", "1.04" to "C57", "1.05" to "F57", "1.06" to "I57", "2.02" to "D115", "2.03" to "G115").forEach { (id, ref) ->
            if (report.photos[id]?.isNotBlank() == true) t(ref, id, 1)
        }

        val rows = cells.groupBy { it.ref.filter(Char::isDigit).toInt() }.toSortedMap()
        val sheetData = buildString {
            append("<sheetData>")
            rows.forEach { (row, rowCells) ->
                val height = rowHeight(row)
                append("<row r=\"$row\" ht=\"$height\" customHeight=\"1\">")
                rowCells.sortedBy { columnIndex(it.ref) }.forEach { c ->
                    if (c.numeric) append("<c r=\"${c.ref}\" s=\"${c.style}\"><v>${c.value}</v></c>")
                    else append("<c r=\"${c.ref}\" s=\"${c.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(c.value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData>")
        }

        val merges = listOf(
            "C1:K1", "C2:D2", "C3:D3", "C4:D4", "C5:D5",
            "D8:G8", "J8:J15", "K8:K15", "D10:G10", "D11:G11", "D12:G12", "D13:G13", "D14:G14", "D15:G15", "C17:G17", "C18:G20", "C21:D21", "E21:G21", "C22:D24", "E22:G24",
            "D93:G93", "J93:J102", "K93:K102", "D95:G95", "D96:G96", "D97:G97", "D98:G98", "D99:G99", "D100:G100", "D101:G101", "D102:G102", "C104:G104", "C105:G107", "C108:D108", "E108:G108", "C109:D111", "E109:G111",
            "D148:G148", "J148:J152", "K148:K152", "D150:G150", "D151:G151", "D152:G152", "C154:G154", "C155:G157", "C158:D158", "E158:G158", "C159:D161", "E159:G161",
            "D164:G164", "J164:J173", "K164:K173", "D166:G166", "D167:G167", "D168:G168", "D169:G169", "D170:G170", "D171:G171", "D172:G172", "D173:G173", "C175:G175", "C176:G178", "C179:D179", "E179:G179", "C180:D182", "E180:G182",
            "D185:G185", "H185:K185", "D187:G187", "H187:K187", "D188:G188", "H188:K188", "D189:G189", "H189:K189", "D190:G190", "H190:K190", "D191:G191", "H191:K191", "D192:G192", "H192:K192", "C194:K194", "C195:K197", "C198:E198", "F198:K198", "C199:E201", "F199:K201"
        )
        val mergeXml = "<mergeCells count=\"${merges.size}\">" + merges.joinToString("") { "<mergeCell ref=\"$it\"/>" } + "</mergeCells>"
        val drawingXml = if (hasDrawing) "<drawing r:id=\"rId1\"/>" else ""

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>
<dimension ref="B1:W242"/>
<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>
<cols>
<col min="2" max="2" width="2" customWidth="1"/><col min="3" max="3" width="8" customWidth="1"/><col min="4" max="4" width="32" customWidth="1"/><col min="5" max="5" width="25" customWidth="1"/><col min="6" max="6" width="13" customWidth="1"/><col min="7" max="7" width="24" customWidth="1"/><col min="8" max="8" width="12" customWidth="1"/><col min="9" max="9" width="13" customWidth="1"/><col min="10" max="10" width="12" customWidth="1"/><col min="11" max="11" width="16" customWidth="1"/><col min="14" max="23" width="14" hidden="1" customWidth="1"/>
</cols>
$sheetData
$mergeXml
<pageMargins left="0.25" right="0.25" top="0.35" bottom="0.35" header="0.2" footer="0.2"/>
<pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>
$drawingXml
</worksheet>"""
    }

    private fun addSection(
        cells: MutableList<Cell>,
        report: ReportData,
        section: SectionDef,
        headerRow: Int,
        firstRow: Int,
        commentRow: Int,
        reviewerRow: Int,
        helperRow: Int,
        scoreCell: String,
        ratingCell: String,
        helperColRow: Int
    ) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        fun n(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }
        t("C$headerRow", "Item", 2)
        t("D$headerRow", section.title + if (section.subtitle.isNotBlank()) "\n(${section.subtitle})" else "", 1)
        t("H$headerRow", "Award", 2); t("I$headerRow", "Available Score", 2); t("J$headerRow", "Rating", 2)
        section.questions.forEachIndexed { i, q ->
            val row = firstRow + i
            t("C$row", q.id, 3); t("D$row", q.text, 3); t("H$row", report.answers[q.id].orEmpty(), 3); n("I$row", q.weight ?: 0.0)
        }
        val pct = report.sectionPercent(section) / 100.0
        n(scoreCell, pct, 5)
        t(ratingCell, if (pct > 0.8) "Acceptable" else "To Improve", 1)
        t("C$commentRow", "Comments on ${section.title.substringAfter("- ", section.title)}:", 2)
        t("C${commentRow + 1}", report.comments[section.id].orEmpty(), 7)
        t("C$reviewerRow", "Reviewer - Print Name", 2); t("E$reviewerRow", "Reviewer - Signature", 2)
        t("C${reviewerRow + 1}", report.reviewerName.ifBlank { report.personName }, 3)
        n("O$helperColRow", report.sectionEarned(section))
        n("O${helperColRow + 1}", report.sectionAvailable(section) - report.sectionEarned(section))
        n("O${helperColRow + 2}", pct)
    }

    private fun addClientSection(cells: MutableList<Cell>, report: ReportData) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style) }
        val section = ReportTemplate.section("client")
        t("C185", "Item", 2); t("D185", section.title, 1); t("H185", "Award", 2)
        section.questions.forEachIndexed { i, q ->
            val row = 187 + i
            t("C$row", q.id); t("D$row", q.text); t("H$row", report.answers[q.id].orEmpty())
        }
        t("C194", "Comments on Client items:", 2); t("C195", report.comments[section.id].orEmpty(), 7)
        t("C198", "Reviewer - Print Name", 2); t("F198", "Reviewer - Signature", 2)
        t("C199", report.reviewerName.ifBlank { report.personName })
    }

    private fun sheetRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"""

    private fun drawing(photos: List<Pair<String, File>>): String {
        val anchors = photos.mapIndexed { index, (question, _) ->
            val box = photoBox(question, index)
            """<xdr:twoCellAnchor editAs="oneCell">
<xdr:from><xdr:col>${box.firstCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${box.firstRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>
<xdr:to><xdr:col>${box.lastCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${box.lastRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>
<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${index + 2}" name="Evidence ${xml(question)}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>
<xdr:blipFill><a:blip r:embed="rId${index + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>
<xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/>
</xdr:twoCellAnchor>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">$anchors</xdr:wsDr>"""
    }

    private fun drawingRels(photos: List<Pair<String, File>>): String {
        val rels = photos.mapIndexed { index, (_, file) ->
            "<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${index + 1}.${imageExtension(file)}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>"""
    }

    private data class PhotoBox(val firstCol: Int, val firstRow: Int, val lastCol: Int, val lastRow: Int)

    private fun photoBox(question: String, fallbackIndex: Int): PhotoBox = when (question) {
        "1.01" -> PhotoBox(2, 27, 5, 55)
        "1.02" -> PhotoBox(5, 27, 8, 55)
        "1.03" -> PhotoBox(8, 27, 11, 55)
        "1.04" -> PhotoBox(2, 57, 5, 88)
        "1.05" -> PhotoBox(5, 57, 8, 88)
        "1.06" -> PhotoBox(8, 57, 11, 88)
        "2.02" -> PhotoBox(3, 115, 6, 145)
        "2.03" -> PhotoBox(6, 115, 9, 145)
        else -> {
            val row = 205 + (fallbackIndex * 12)
            PhotoBox(3, row, 9, row + 10)
        }
    }

    private fun imageExtension(file: File): String = if (file.extension.lowercase() == "png") "png" else "jpg"

    private fun rowHeight(row: Int): Int = when {
        row == 1 -> 24
        row in 2..5 -> 22
        row in listOf(8, 93, 148, 164, 185) -> 28
        row in 27..88 || row in 115..145 -> 18
        else -> 20
    }

    private fun displayDate(iso: String): String = try {
        val p = iso.split("-")
        "${p[2]}/${p[1]}/${p[0]}"
    } catch (_: Exception) { iso }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        var value = 0
        letters.forEach { value = value * 26 + (it.uppercaseChar() - 'A' + 1) }
        return value
    }

    private fun safeSheetName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\[\]]"), "-").trim().ifBlank { "New Report" }
        return cleaned.take(31)
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
