package com.layababateam.xinxiwang_backend.service

import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class ExcelExportService {

    companion object {
        const val MAX_EXPORT_ROWS = 50000
    }

    fun exportToExcel(
        headers: List<String>,
        rows: List<List<Any?>>,
        sheetName: String = "Sheet1"
    ): ByteArray {
        val workbook = SXSSFWorkbook(200)
        try {
            val sheet = workbook.createSheet(sheetName)

            val headerStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont()
                font.bold = true
                setFont(font)
            }

            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }

            val limitedRows = if (rows.size > MAX_EXPORT_ROWS) {
                rows.take(MAX_EXPORT_ROWS)
            } else {
                rows
            }

            limitedRows.forEachIndexed { rowIndex, rowData ->
                val row = sheet.createRow(rowIndex + 1)
                rowData.forEachIndexed { colIndex, value ->
                    val cell = row.createCell(colIndex)
                    when (value) {
                        null -> cell.setCellValue("")
                        is Number -> cell.setCellValue(value.toString())
                        else -> cell.setCellValue(value.toString())
                    }
                }
            }

            headers.indices.forEach { colIndex ->
                sheet.setColumnWidth(colIndex, 20 * 256)
            }

            val outputStream = ByteArrayOutputStream()
            workbook.write(outputStream)
            return outputStream.toByteArray()
        } finally {
            workbook.close()
        }
    }
}
