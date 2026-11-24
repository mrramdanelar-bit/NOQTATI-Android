package com.example.export

import android.content.Context
import com.example.data.preferences.AppSettings
import com.example.data.repository.formatScore
import com.example.domain.ClassSemesterSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates genuine Office Open XML (.xlsx) Excel workbooks with RTL sheet direction,
 * UTF-8 Arabic text, styled header row, and numeric cells — with zero external libraries.
 */
class ExcelExportGenerator(private val context: Context) {

    suspend fun generateClassGradesXlsx(
        summary: ClassSemesterSummary,
        settings: AppSettings
    ): File = withContext(Dispatchers.IO) {
        val subjects = summary.reports.firstOrNull()?.lines?.map { it.subject } ?: emptyList()

        val rows = mutableListOf<List<CellValue>>()

        // Title Row
        val title = buildString {
            append("كشف علامات القسم: ${summary.academicClass.name} — ${summary.semester.name} (${summary.academicClass.academicYear})")
            if (settings.schoolName.isNotBlank()) append(" | ${settings.schoolName}")
        }
        rows.add(listOf(CellValue.Text(title, styleIndex = 1)))

        // Header Row
        val header = mutableListOf<CellValue>(
            CellValue.Text("الترتيب", styleIndex = 1),
            CellValue.Text("رقم التسجيل", styleIndex = 1),
            CellValue.Text("اللقب والاسم", styleIndex = 1)
        )
        subjects.forEach { subj ->
            header.add(CellValue.Text("${subj.name} (م ${formatScore(subj.coefficient)})", styleIndex = 1))
        }
        header.add(CellValue.Text("المعدل الفصلي", styleIndex = 1))
        header.add(CellValue.Text("التقدير", styleIndex = 1))
        rows.add(header)

        // Student Rows
        summary.reports.forEach { report ->
            val row = mutableListOf<CellValue>()
            row.add(report.rank?.let { CellValue.Number(it.toDouble()) } ?: CellValue.Text("—"))
            row.add(CellValue.Text(report.student.registrationNumber))
            row.add(CellValue.Text(report.student.fullName))

            subjects.forEach { subj ->
                val grade = report.lines.firstOrNull { it.subject.id == subj.id }?.grade
                if (grade != null) {
                    row.add(CellValue.Number(grade.finalScore))
                } else {
                    row.add(CellValue.Text("—"))
                }
            }

            row.add(report.average?.let { CellValue.Number(it, styleIndex = 2) } ?: CellValue.Text("—"))
            row.add(CellValue.Text(report.appreciation))
            rows.add(row)
        }

        // Summary Row
        rows.add(emptyList())
        rows.add(
            listOf(
                CellValue.Text("إحصائيات القسم", styleIndex = 1),
                CellValue.Text("المعدل العام:"),
                summary.classAverage?.let { CellValue.Number(it, styleIndex = 2) } ?: CellValue.Text("—"),
                CellValue.Text("أعلى معدل:"),
                summary.highestAverage?.let { CellValue.Number(it) } ?: CellValue.Text("—"),
                CellValue.Text("نسبة النجاح %:"),
                summary.passRatePercent?.let { CellValue.Number(it) } ?: CellValue.Text("—")
            )
        )

        val sheetXml = buildWorksheetXml(rows)
        val outDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(outDir, "noqtati_class_${summary.academicClass.id}_${System.currentTimeMillis()}.xlsx")

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
        file
    }

    private sealed interface CellValue {
        val styleIndex: Int
        data class Text(val value: String, override val styleIndex: Int = 0) : CellValue
        data class Number(val value: Double, override val styleIndex: Int = 0) : CellValue
    }

    private fun buildWorksheetXml(rows: List<List<CellValue>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView rightToLeft="1" tabSelected="1" workbookViewId="0"/></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="22"/>""")
        sb.append("""<cols><col min="1" max="1" width="10" customWidth="1"/><col min="2" max="2" width="16" customWidth="1"/><col min="3" max="3" width="26" customWidth="1"/><col min="4" max="25" width="16" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cell ->
                val ref = "${columnLetter(cIdx)}$rowNum"
                when (cell) {
                    is CellValue.Text -> {
                        sb.append("""<c r="$ref" t="inlineStr" s="${cell.styleIndex}"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                    }
                    is CellValue.Number -> {
                        sb.append("""<c r="$ref" s="${cell.styleIndex}"><v>${cell.value}</v></c>""")
                    }
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun columnLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    companion object {
        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="كشف النقاط" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Arial"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F6B5E"/><name val="Arial"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F6B5E"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="2" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""
    }
}
