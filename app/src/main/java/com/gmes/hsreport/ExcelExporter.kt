package com.gmes.hsreport

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a macro-free XLSX using the same key FM_038 cell positions as the
 * existing New Report sheet. The workbook is intentionally simple: the app is
 * the data-entry/scoring layer and the XLSX is an interchange/export format.
 */
object ExcelExporter {
    private const val TEAL = "0C6E6A"

    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, report: ReportData): ByteArray {
        val photos = report.photos.entries.mapNotNull { (id, path) ->
            File(path).takeIf { it.exists() }?.let { id to it }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun textEntry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            textEntry("[Content_Types].xml", contentTypes(photos))
            textEntry("_rels/.rels", rootRels())
            textEntry("docProps/app.xml", appProps())
            textEntry("docProps/core.xml", coreProps())
            textEntry("xl/workbook.xml", workbookXml(report))
            textEntry("xl/_rels/workbook.xml.rels", workbookRels())
            textEntry("xl/styles.xml", stylesXml())
            textEntry("xl/worksheets/sheet1.xml", sheetXml(report, photos.isNotEmpty()))

            if (photos.isNotEmpty()) {
                textEntry("xl/worksheets/_rels/sheet1.xml.rels", sheetRels())
                textEntry("xl/drawings/drawing1.xml", drawingXml(photos))
                textEntry("xl/drawings/_rels/drawing1.xml.rels", drawingRels(photos))
                photos.forEachIndexed { index, (_, file) ->
                    val ext = imageExt(file)
                    zip.putNextEntry(ZipEntry("xl/media/image${index + 1}.$ext"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun contentTypes(photos: List<Pair<String, File>>): String {
        val imageTypes = buildString {
            if (photos.any { imageExt(it.second) == "jpg" }) append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
            if (photos.any { imageExt(it.second) == "png" }) append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        }
        val drawing = if (photos.isNotEmpty()) {
            "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>"
        } else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
$imageTypes
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
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>GMES H&amp;S Report App</Application></Properties>"""

    private fun coreProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator>GMES H&amp;S Report App</dc:creator></cp:coreProperties>"""

    private fun workbookXml(report: ReportData): String {
        val name = safeSheetName(report.projectName.ifBlank { "New Report" })
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${xml(name)}" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'${xml(name.replace("'", "''"))}'!\$B\$1:\$L\$242</definedName></definedNames>
</workbook>"""
    }

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4"><font><sz val="9"/><name val="Arial"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="9"/><name val="Arial"/></font><font><b/><sz val="9"/><name val="Arial"/></font><font><b/><sz val="12"/><name val="Arial"/></font></fonts>
<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF$TEAL"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/></patternFill></fill></fills>
<borders count="2"><border/><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="10" fontId="2" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private data class Cell(val ref: String, val value: String, val style: Int = 3, val numeric: Boolean = false)

    private fun sheetXml(report: ReportData, hasDrawing: Boolean): String {
        val cells = mutableListOf<Cell>()
        fun text(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style, false) }
        fun num(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }

        text("C1", "Group Management Electrical Surveys", 6)
        text("C2", "Client:", 1); text("E2", report.client)
        text("F2", "Site Visit Date:", 1); text("G2", displayDate(report.visitDate))
        text("C3", "Project Name:", 1); text("E3", report.projectName)
        text("F3", "GMES Project No:", 1); text("G3", report.projectNo)
        text("C4", "Person Completing Report & Role:", 1); text("E4", joinNameRole(report.personName, report.personRole))
        text("F4", "Sector:", 1); text("G4", report.sector)
        text("C5", "GMES Site Manager/Supervisor & Role:", 1); text("E5", joinNameRole(report.managerName, report.managerRole))
        text("F5", "Territory:", 1); text("G5", report.territory)
        num("R3", 1.0); num("R4", report.overallPercent() / 100.0); num("R5", 1.0 - report.overallPercent() / 100.0)

        addScoredSection(cells, report, ReportTemplate.section("policy"), 8, 10, 17, 21, 13, "J10", "K10")
        addScoredSection(cells, report, ReportTemplate.section("ppe"), 93, 95, 104, 108, 100, "J95", "K95")
        addScoredSection(cells, report, ReportTemplate.section("equipment"), 148, 150, 154, 158, 150, "J150", "K150")
        addScoredSection(cells, report, ReportTemplate.section("operational"), 164, 166, 175, 179, 171, "J166", "K166")
        addClientSection(cells, report)

        val scored = ReportTemplate.sections.filter { it.scored }
        text("U18", "Section", 2); text("V18", "Completed %", 2); text("W18", "Missing %", 2)
        val summary = listOf(
            "Overall Score" to report.overallPercent(),
            "Policy Documents" to report.sectionPercent(scored[0]),
            "PPE/Training/Inspections" to report.sectionPercent(scored[1]),
            "Equipment" to report.sectionPercent(scored[2]),
            "Operational" to report.sectionPercent(scored[3])
        )
        summary.forEachIndexed { index, (name, pct) ->
            val row = 19 + index
            text("U$row", name, 2); num("V$row", pct / 100.0); num("W$row", 1.0 - pct / 100.0)
        }

        val rows = cells.groupBy { it.ref.filter(Char::isDigit).toInt() }.toSortedMap()
        val sheetData = buildString {
            append("<sheetData>")
            rows.forEach { (row, rowCells) ->
                append("<row r=\"$row\" ht=\"${rowHeight(row)}\" customHeight=\"1\">")
                rowCells.sortedBy { colIndex(it.ref) }.forEach { cell ->
                    if (cell.numeric) append("<c r=\"${cell.ref}\" s=\"${cell.style}\"><v>${cell.value}</v></c>")
                    else append("<c r=\"${cell.ref}\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(cell.value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData>")
        }

        val merges = listOf(
            "C1:K1", "C2:D2", "C3:D3", "C4:D4", "C5:D5",
            "D8:G8", "J8:K8", "J10:J15", "K10:K15", "C17:G17", "C18:G20", "C21:D21", "E21:G21", "C22:D24", "E22:G24",
            "D93:G93", "J93:K93", "J95:J102", "K95:K102", "C104:G104", "C105:G107", "C108:D108", "E108:G108", "C109:D111", "E109:G111",
            "D148:G148", "J148:K148", "J150:J152", "K150:K152", "C154:G154", "C155:G157", "C158:D158", "E158:G158", "C159:D161", "E159:G161",
            "D164:G164", "J164:K164", "J166:J173", "K166:K173", "C175:G175", "C176:G178", "C179:D179", "E179:G179", "C180:D182", "E180:G182",
            "D185:G185", "H185:K185", "C194:K194", "C195:K197", "C198:E198", "F198:K198", "C199:E201", "F199:K201"
        )
        val mergeXml = "<mergeCells count=\"${merges.size}\">" + merges.joinToString("") { "<mergeCell ref=\"$it\"/>" } + "</mergeCells>"
        val drawing = if (hasDrawing) "<drawing r:id=\"rId1\"/>" else ""

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><dimension ref="B1:W242"/><sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>
<cols><col min="2" max="2" width="2"/><col min="3" max="3" width="8"/><col min="4" max="4" width="32"/><col min="5" max="5" width="25"/><col min="6" max="6" width="13"/><col min="7" max="7" width="24"/><col min="8" max="8" width="12"/><col min="9" max="9" width="13"/><col min="10" max="10" width="12"/><col min="11" max="11" width="16"/><col min="14" max="23" hidden="1"/></cols>
$sheetData$mergeXml<pageMargins left="0.25" right="0.25" top="0.35" bottom="0.35" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>$drawing
</worksheet>"""
    }

    private fun addScoredSection(cells: MutableList<Cell>, report: ReportData, section: SectionDef, headerRow: Int, firstRow: Int, commentRow: Int, reviewerRow: Int, helperRow: Int, scoreCell: String, ratingCell: String) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style, false) }
        fun n(ref: String, value: Double, style: Int = 4) { cells += Cell(ref, value.toString(), style, true) }
        t("C$headerRow", "Item", 2); t("D$headerRow", section.title, 1); t("H$headerRow", "Award", 2); t("I$headerRow", "Available Score", 2); t("J$headerRow", "Rating", 2)
        section.questions.forEachIndexed { index, q ->
            val row = firstRow + index
            t("C$row", q.id); t("D$row", q.text); t("H$row", report.answers[q.id].orEmpty()); n("I$row", q.weight ?: 0.0)
        }
        val pct = report.sectionPercent(section) / 100.0
        n(scoreCell, pct, 5); t(ratingCell, if (pct > 0.8) "Acceptable" else "To Improve", 1)
        t("C$commentRow", "Comments on ${section.title.substringAfter("- ", section.title)}:", 2)
        t("C${commentRow + 1}", report.comments[section.id].orEmpty(), 7)
        t("C$reviewerRow", "Reviewer - Print Name", 2); t("E$reviewerRow", "Reviewer - Signature", 2)
        t("C${reviewerRow + 1}", report.reviewerName.ifBlank { report.personName })
        n("O$helperRow", report.sectionEarned(section)); n("O${helperRow + 1}", report.sectionAvailable(section) - report.sectionEarned(section)); n("O${helperRow + 2}", pct)
    }

    private fun addClientSection(cells: MutableList<Cell>, report: ReportData) {
        fun t(ref: String, value: String, style: Int = 3) { cells += Cell(ref, value, style, false) }
        val section = ReportTemplate.section("client")
        t("C185", "Item", 2); t("D185", section.title, 1); t("H185", "Award", 2)
        section.questions.forEachIndexed { index, q ->
            val row = 187 + index
            t("C$row", q.id); t("D$row", q.text); t("H$row", report.answers[q.id].orEmpty())
        }
        t("C194", "Comments on Client items:", 2); t("C195", report.comments[section.id].orEmpty(), 7)
        t("C198", "Reviewer - Print Name", 2); t("F198", "Reviewer - Signature", 2)
        t("C199", report.reviewerName.ifBlank { report.personName })
    }

    private fun sheetRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"""

    private data class Box(val c1: Int, val r1: Int, val c2: Int, val r2: Int)

    private fun drawingXml(photos: List<Pair<String, File>>): String {
        val body = photos.mapIndexed { index, (id, _) ->
            val b = photoBox(id, index)
            """<xdr:twoCellAnchor editAs="oneCell"><xdr:from><xdr:col>${b.c1}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r1}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>${b.c2}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r2}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${index + 2}" name="Evidence ${xml(id)}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId${index + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">$body</xdr:wsDr>"""
    }

    private fun drawingRels(photos: List<Pair<String, File>>): String {
        val body = photos.mapIndexed { index, (_, file) ->
            "<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${index + 1}.${imageExt(file)}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$body</Relationships>"""
    }

    private fun photoBox(id: String, fallback: Int): Box = when (id) {
        "1.01" -> Box(2, 27, 5, 55); "1.02" -> Box(5, 27, 8, 55); "1.03" -> Box(8, 27, 11, 55)
        "1.04" -> Box(2, 57, 5, 88); "1.05" -> Box(5, 57, 8, 88); "1.06" -> Box(8, 57, 11, 88)
        "2.02" -> Box(3, 115, 6, 145); "2.03" -> Box(6, 115, 9, 145)
        else -> Box(3, 205 + fallback * 12, 9, 215 + fallback * 12)
    }

    private fun imageExt(file: File) = if (file.extension.lowercase() == "png") "png" else "jpg"
    private fun rowHeight(row: Int) = when { row == 1 -> 24; row in 2..5 -> 22; row in listOf(8, 93, 148, 164, 185) -> 28; else -> 20 }
    private fun displayDate(iso: String) = try { val p = iso.split("-"); "${p[2]}/${p[1]}/${p[0]}" } catch (_: Exception) { iso }
    private fun joinNameRole(name: String, role: String) = listOf(name, role).filter { it.isNotBlank() }.joinToString(" - ")
    private fun colIndex(ref: String): Int { var n = 0; ref.takeWhile { it.isLetter() }.forEach { n = n * 26 + (it.uppercaseChar() - 'A' + 1) }; return n }
    private fun safeSheetName(value: String): String = value.replace(Regex("[\\\\/:*?\\[\\]]"), "-").trim().ifBlank { "New Report" }.take(31)
    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
