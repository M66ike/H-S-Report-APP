package com.gmes.hsreport

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Macro-free XLSX export using the important FM_038 New Report cell positions. */
object ExcelExporter {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, report: ReportData): ByteArray {
        val photos = report.photos.mapNotNull { (id, path) -> File(path).takeIf(File::exists)?.let { id to it } }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(photos))
            put("_rels/.rels", rootRels)
            put("xl/workbook.xml", workbook(report))
            put("xl/_rels/workbook.xml.rels", workbookRels)
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheet(report, photos.isNotEmpty()))
            if (photos.isNotEmpty()) {
                put("xl/worksheets/_rels/sheet1.xml.rels", sheetRels)
                put("xl/drawings/drawing1.xml", drawing(photos))
                put("xl/drawings/_rels/drawing1.xml.rels", drawingRels(photos))
                photos.forEachIndexed { i, (_, file) ->
                    val ext = ext(file)
                    zip.putNextEntry(ZipEntry("xl/media/image${i + 1}.$ext")); zip.write(file.readBytes()); zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun contentTypes(photos: List<Pair<String, File>>) = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
${if (photos.any { ext(it.second) == "jpg" }) "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" else ""}
${if (photos.any { ext(it.second) == "png" }) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""}
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
${if (photos.isNotEmpty()) "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>" else ""}
</Types>"""

    private val rootRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(report: ReportData): String {
        val n = safeSheetName(report.projectName.ifBlank { "New Report" })
        val dollar = '$'
        return """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${xml(n)}" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'${xml(n.replace("'", "''"))}'!${dollar}B${dollar}1:${dollar}L${dollar}242</definedName></definedNames>
</workbook>"""
    }

    private val workbookRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3"><font><sz val="9"/><name val="Arial"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="9"/><name val="Arial"/></font><font><b/><sz val="9"/><name val="Arial"/></font></fonts>
<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0C6E6A"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/></patternFill></fill></fills>
<borders count="2"><border/><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="10" fontId="2" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="10" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private data class C(val ref: String, val value: String, val style: Int = 3, val number: Boolean = false)

    private fun sheet(report: ReportData, hasDrawing: Boolean): String {
        val cells = mutableListOf<C>()
        fun t(r: String, v: String, s: Int = 3) { cells += C(r, v, s) }
        fun n(r: String, v: Double, s: Int = 4) { cells += C(r, v.toString(), s, true) }

        t("C1", "Group Management Electrical Surveys", 2)
        t("C2", "Client:", 1); t("E2", report.client)
        t("F2", "Site Visit Date:", 1); t("G2", display(report.visitDate))
        t("C3", "Project Name:", 1); t("E3", report.projectName)
        t("F3", "GMES Project No:", 1); t("G3", report.projectNo)
        t("C4", "Person Completing Report & Role:", 1); t("E4", join(report.personName, report.personRole))
        t("F4", "Sector:", 1); t("G4", report.sector)
        t("C5", "GMES Site Manager/Supervisor & Role:", 1); t("E5", join(report.managerName, report.managerRole))
        t("F5", "Territory:", 1); t("G5", report.territory)
        n("R3", 1.0); n("R4", report.overallPercent() / 100.0); n("R5", 1 - report.overallPercent() / 100.0)

        scored(cells, report, ReportTemplate.section("policy"), 8, 10, 17, 21, "J10", "K10")
        scored(cells, report, ReportTemplate.section("ppe"), 93, 95, 104, 108, "J95", "K95")
        scored(cells, report, ReportTemplate.section("equipment"), 148, 150, 154, 158, "J150", "K150")
        scored(cells, report, ReportTemplate.section("operational"), 164, 166, 175, 179, "J166", "K166")
        client(cells, report)

        val scoredSections = ReportTemplate.sections.filter { it.scored }
        val summary = listOf("Overall Score" to report.overallPercent()) + scoredSections.map { it.title.substringAfter("- ").trim() to report.sectionPercent(it) }
        t("U18", "Section", 2); t("V18", "Completed %", 2); t("W18", "Missing %", 2)
        summary.forEachIndexed { i, item -> val row = 19 + i; t("U$row", item.first); n("V$row", item.second / 100.0); n("W$row", 1 - item.second / 100.0) }

        val rows = cells.groupBy { it.ref.filter(Char::isDigit).toInt() }.toSortedMap()
        val body = buildString {
            append("<sheetData>")
            rows.forEach { (row, cs) ->
                append("<row r=\"$row\">")
                cs.sortedBy { col(it.ref) }.forEach { c ->
                    if (c.number) append("<c r=\"${c.ref}\" s=\"${c.style}\"><v>${c.value}</v></c>")
                    else append("<c r=\"${c.ref}\" s=\"${c.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(c.value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData>")
        }
        val drawing = if (hasDrawing) "<drawing r:id=\"rId1\"/>" else ""
        return """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<dimension ref="B1:W242"/><sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>
<cols><col min="2" max="2" width="2"/><col min="3" max="3" width="8"/><col min="4" max="4" width="32"/><col min="5" max="7" width="22"/><col min="8" max="11" width="14"/><col min="14" max="23" hidden="1"/></cols>
$body<pageMargins left="0.25" right="0.25" top="0.35" bottom="0.35" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>$drawing
</worksheet>"""
    }

    private fun scored(cells: MutableList<C>, report: ReportData, section: SectionDef, header: Int, first: Int, comments: Int, reviewer: Int, scoreCell: String, ratingCell: String) {
        fun t(r: String, v: String, s: Int = 3) { cells += C(r, v, s) }; fun n(r: String, v: Double, s: Int = 4) { cells += C(r, v.toString(), s, true) }
        t("C$header", "Item", 2); t("D$header", section.title, 1); t("H$header", "Award", 2); t("I$header", "Available Score", 2); t("J$header", "Rating", 2)
        section.questions.forEachIndexed { i, q -> val row = first + i; t("C$row", q.id); t("D$row", q.text); t("H$row", report.answers[q.id].orEmpty()); n("I$row", q.weight ?: 0.0) }
        val p = report.sectionPercent(section) / 100.0; n(scoreCell, p, 5); t(ratingCell, if (p > .8) "Acceptable" else "To Improve", 1)
        t("C$comments", "Comments:", 2); t("C${comments + 1}", report.comments[section.id].orEmpty())
        t("C$reviewer", "Reviewer - Print Name", 2); t("E$reviewer", "Reviewer - Signature", 2); t("C${reviewer + 1}", report.reviewerName.ifBlank { report.personName })
    }

    private fun client(cells: MutableList<C>, report: ReportData) {
        fun t(r: String, v: String, s: Int = 3) { cells += C(r, v, s) }
        val s = ReportTemplate.section("client"); t("C185", "Item", 2); t("D185", s.title, 1); t("H185", "Award", 2)
        s.questions.forEachIndexed { i, q -> val row = 187 + i; t("C$row", q.id); t("D$row", q.text); t("H$row", report.answers[q.id].orEmpty()) }
        t("C194", "Comments on Client items:", 2); t("C195", report.comments[s.id].orEmpty()); t("C198", "Reviewer - Print Name", 2); t("F198", "Reviewer - Signature", 2); t("C199", report.reviewerName.ifBlank { report.personName })
    }

    private val sheetRels = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"""
    private data class B(val c1: Int, val r1: Int, val c2: Int, val r2: Int)
    private fun box(id: String, i: Int) = when (id) {
        "1.01" -> B(2,27,5,55); "1.02" -> B(5,27,8,55); "1.03" -> B(8,27,11,55); "1.04" -> B(2,57,5,88); "1.05" -> B(5,57,8,88); "1.06" -> B(8,57,11,88); "2.02" -> B(3,115,6,145); "2.03" -> B(6,115,9,145); else -> B(3,205+i*10,9,213+i*10)
    }
    private fun drawing(photos: List<Pair<String, File>>) = """<?xml version="1.0" encoding="UTF-8"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">${photos.mapIndexed { i, p -> val b=box(p.first,i); "<xdr:twoCellAnchor><xdr:from><xdr:col>${b.c1}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r1}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>${b.c2}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${b.r2}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"${i+2}\" name=\"Evidence ${xml(p.first)}\"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed=\"rId${i+1}\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>" }.joinToString("")}</xdr:wsDr>"""
    private fun drawingRels(photos: List<Pair<String, File>>) = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${photos.mapIndexed { i, p -> "<Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${i+1}.${ext(p.second)}\"/>" }.joinToString("")}</Relationships>"""

    private fun ext(f: File) = if (f.extension.lowercase() == "png") "png" else "jpg"
    private fun safeSheetName(v: String) = v.replace(Regex("[\\\\/:*?\\[\\]]"), "-").trim().ifBlank { "New Report" }.take(31)
    private fun join(a: String, b: String) = listOf(a,b).filter(String::isNotBlank).joinToString(" - ")
    private fun display(v: String) = try { val p=v.split("-"); "${p[2]}/${p[1]}/${p[0]}" } catch (_: Exception) { v }
    private fun col(ref: String): Int { var n=0; ref.takeWhile(Char::isLetter).forEach { n=n*26+(it.uppercaseChar()-'A'+1) }; return n }
    private fun xml(v: String) = v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
