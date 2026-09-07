package com.acruzdb.misfinanzas.statements;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Genera ficheros .xlsx reales en memoria para los tests del módulo
 * {@code statements}, evitando duplicar esta lógica en cada test class
 * que necesite un Excel de prueba.
 */
public final class TestExcelFactory {

    private TestExcelFactory() {
    }

    /**
     * Construye un .xlsx con las cabeceras y filas dadas.
     *
     * @param headers cabeceras de la primera fila
     * @param rows    filas de datos; cada valor se convierte a texto tal cual
     * @return el fichero, listo para pasar a un servicio como {@link org.springframework.web.multipart.MultipartFile}
     */
    public static MockMultipartFile buildExcel(String[] headers, Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Movimientos");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(String.valueOf(rows[r][c]));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}