package com.acruzdb.misfinanzas.statements.application;

import com.acruzdb.misfinanzas.statements.dto.StatementPreviewResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link ExcelParsingService}.
 * <p>
 * A diferencia de los tests de Service habituales, aquí no hay
 * repositorios que mockear: la propia clase bajo prueba solo depende
 * de Apache POI, así que generamos ficheros .xlsx reales en memoria
 * (con la misma librería) en vez de simular nada con Mockito.
 */
class ExcelParsingServiceTest {

    private final ExcelParsingService service = new ExcelParsingService();

    @Test
    @DisplayName("preview() detecta automáticamente fecha, descripción e importe por el nombre de cabecera")
    void preview_detectaMapeoPorCabeceras() throws IOException {
        MockMultipartFile file = buildExcel(
                new String[]{"Fecha", "Concepto", "Importe", "Saldo"},
                new Object[][]{
                        {"01/09/2026", "Mercadona", "-64.20", "3056.35"},
                        {"02/09/2026", "Nomina", "2450.00", "5506.35"},
                }
        );

        StatementPreviewResponse response = service.preview(file);

        assertThat(response.headers()).containsExactly("Fecha", "Concepto", "Importe", "Saldo");
        assertThat(response.sampleRows()).hasSize(2);
        assertThat(response.suggestedMapping().dateColumn()).isEqualTo("Fecha");
        assertThat(response.suggestedMapping().descriptionColumn()).isEqualTo("Concepto");
        assertThat(response.suggestedMapping().amountColumn()).isEqualTo("Importe");
    }

    @Test
    @DisplayName("preview() no reconoce cabeceras en un idioma o formato inusual, y deja el mapeo a null")
    void preview_dejaSinMapearCabecerasNoReconocidas() throws IOException {
        MockMultipartFile file = buildExcel(
                new String[]{"Transaction Date", "Merchant", "Value"},
                new Object[][]{{"01/09/2026", "Amazon", "-30.00"}}
        );

        StatementPreviewResponse response = service.preview(file);

        // Ninguna cabecera coincide con las pistas en español -> el usuario
        // tendrá que mapear a mano, que es el comportamiento correcto,
        // no un fallo: mejor "no sé" explícito que una suposición errónea.
        assertThat(response.suggestedMapping().dateColumn()).isNull();
        assertThat(response.suggestedMapping().descriptionColumn()).isNull();
        assertThat(response.suggestedMapping().amountColumn()).isNull();
    }

    @Test
    @DisplayName("preview() lanza 400 si el fichero no es un Excel válido")
    void preview_lanza400SiNoEsExcelValido() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "no-es-excel.xlsx", "application/octet-stream", "esto no es un excel".getBytes()
        );

        assertThatThrownBy(() -> service.preview(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se pudo leer");
    }

    /**
     * Construye un .xlsx real en memoria con las cabeceras y filas dadas,
     * envuelto en un {@link MockMultipartFile} listo para pasar al servicio
     * — así probamos contra bytes de Excel de verdad, no una simulación.
     */
    private MockMultipartFile buildExcel(String[] headers, Object[][] rows) throws IOException {
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