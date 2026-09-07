package com.acruzdb.misfinanzas.statements.application;

import com.acruzdb.misfinanzas.statements.dto.ColumnMapping;
import com.acruzdb.misfinanzas.statements.dto.StatementPreviewResponse;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

/**
 * Lee ficheros Excel de cualquier banco de forma agnóstica al formato
 * concreto: no asume nombres ni orden de columnas fijos.
 * <p>
 * El proceso tiene dos fases separadas a propósito: {@link #preview}
 * solo lee y sugiere un mapeo, sin persistir nada; la importación real
 * (siguiente paso, en {@code StatementImportService}) recibe el mapeo
 * ya confirmado por el usuario y ahí sí crea movimientos.
 */
@Service
public class ExcelParsingService {

    private static final int MAX_PREVIEW_ROWS = 8;

    /**
     * Cabeceras habituales en extractos bancarios españoles, usadas para
     * sugerir un mapeo automático. No es una lista cerrada: si ninguna
     * coincide, el campo correspondiente queda a null y el usuario lo
     * rellena a mano — la detección es una ayuda, no un requisito.
     */
    private static final Map<String, List<String>> HEADER_HINTS = Map.of(
            "date", List.of("fecha", "fecha operación", "fecha valor", "date"),
            "description", List.of("concepto", "descripción", "descripcion", "detalle", "movimiento"),
            "amount", List.of("importe", "cantidad", "amount", "valor")
    );

    /**
     * Analiza el fichero subido: extrae cabeceras, unas filas de muestra,
     * y sugiere un mapeo de columnas por coincidencia de nombre.
     *
     * @param file fichero .xlsx subido por el usuario
     * @return cabeceras, filas de muestra y mapeo sugerido
     * @throws ResponseStatusException 400 si el fichero no se puede leer
     *         como Excel, o si no tiene ninguna fila
     */
    public StatementPreviewResponse preview(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El fichero no tiene filas");
            }

            List<String> headers = readHeaderRow(headerRow);
            List<List<String>> sampleRows = readSampleRows(sheet, headerRow.getRowNum() + 1, headers.size());
            ColumnMapping suggested = suggestMapping(headers);

            return new StatementPreviewResponse(headers, sampleRows, suggested);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el fichero como Excel", e);
        }
    }

    private List<String> readHeaderRow(Row headerRow) {
        List<String> headers = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            headers.add(formatter.formatCellValue(cell).trim());
        }
        return headers;
    }

    private List<List<String>> readSampleRows(Sheet sheet, int firstDataRowIndex, int columnCount) {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        int lastRow = Math.min(sheet.getLastRowNum(), firstDataRowIndex + MAX_PREVIEW_ROWS - 1);

        for (int r = firstDataRowIndex; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            List<String> values = new ArrayList<>();
            for (int c = 0; c < columnCount; c++) {
                Cell cell = row.getCell(c);
                values.add(cell != null ? formatter.formatCellValue(cell).trim() : "");
            }
            rows.add(values);
        }
        return rows;
    }

    /**
     * Intenta adivinar qué cabecera corresponde a fecha/descripción/importe
     * comparando (sin distinguir mayúsculas ni acentos) contra la lista de
     * nombres habituales. Es una heurística simple a propósito: mejor una
     * sugerencia que el usuario puede corregir en dos clics, que un
     * sistema "inteligente" que falla de forma opaca.
     */
    private ColumnMapping suggestMapping(List<String> headers) {
        String dateCol = findFirstMatch(headers, HEADER_HINTS.get("date"));
        String descCol = findFirstMatch(headers, HEADER_HINTS.get("description"));
        String amountCol = findFirstMatch(headers, HEADER_HINTS.get("amount"));
        return new ColumnMapping(dateCol, descCol, amountCol, null);
    }

    private String findFirstMatch(List<String> headers, List<String> candidates) {
        for (String header : headers) {
            String normalized = normalize(header);
            if (candidates.stream().anyMatch(normalized::equals)) {
                return header;
            }
        }
        return null;
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u")
                .trim();
    }
}