package com.acruzdb.misfinanzas.statements.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.statements.domain.StatementImport;
import com.acruzdb.misfinanzas.statements.dto.ColumnMapping;
import com.acruzdb.misfinanzas.statements.dto.StatementImportResponse;
import com.acruzdb.misfinanzas.statements.infrastructure.StatementImportRepository;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Convierte un fichero Excel, junto con un mapeo de columnas ya
 * confirmado, en movimientos reales guardados en base de datos.
 * <p>
 * A diferencia de {@link ExcelParsingService#preview}, aquí cada fila
 * se procesa de forma tolerante: una fila con una fecha o importe que
 * no se puede interpretar se cuenta como fallida y se salta, en vez de
 * abortar toda la importación — así un extracto con una fila rara al
 * final no te hace perder las 200 filas buenas anteriores.
 */
@Service
public class StatementImportService {

    // Formatos de fecha que aceptamos, en orden de intento. dd/MM/yyyy es
    // el más común en extractos españoles; ISO (yyyy-MM-dd) se añade como
    // red de seguridad para exports que ya vienen normalizados.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final TransactionRepository transactionRepository;
    private final StatementImportRepository statementImportRepository;

    public StatementImportService(TransactionRepository transactionRepository,
                                  StatementImportRepository statementImportRepository) {
        this.transactionRepository = transactionRepository;
        this.statementImportRepository = statementImportRepository;
    }

    /**
     * Importa un fichero Excel usando el mapeo de columnas indicado.
     *
     * @param file        fichero .xlsx (se vuelve a subir, ya que no
     *                    guardamos el de la vista previa — ver nota de
     *                    alcance del módulo)
     * @param mapping     qué columna es cada campo
     * @param user        usuario propietario de los movimientos creados
     * @param householdId si no es null, los movimientos se crean como
     *                    compartidos en ese household
     * @return resumen de la importación: filas totales, importadas y fallidas
     * @throws ResponseStatusException 400 si el fichero no se puede leer
     */
    @Transactional
    public StatementImportResponse importFile(MultipartFile file, ColumnMapping mapping, User user, UUID householdId) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El fichero no tiene filas");
            }

            int dateColIdx = findColumnIndex(headerRow, mapping.dateColumn());
            int descColIdx = findColumnIndex(headerRow, mapping.descriptionColumn());
            int amountColIdx = findColumnIndex(headerRow, mapping.amountColumn());

            List<String> errors = new ArrayList<>();
            int total = 0, imported = 0, failed = 0;
            DataFormatter formatter = new DataFormatter();

            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, formatter)) continue;
                total++;

                try {
                    Transaction transaction = parseRow(row, formatter, dateColIdx, descColIdx, amountColIdx, user, householdId);
                    transactionRepository.save(transaction);
                    imported++;
                } catch (RowParseException e) {
                    failed++;
                    if (errors.size() < 5) { // no acumulamos un resumen infinito
                        errors.add("Fila " + (r + 1) + ": " + e.getMessage());
                    }
                }
            }

            StatementImport importRecord = new StatementImport(user, householdId, file.getOriginalFilename());
            importRecord.markCompleted(total, imported, failed, errors.isEmpty() ? null : String.join("; ", errors));
            statementImportRepository.save(importRecord);

            return StatementImportResponse.from(importRecord);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el fichero como Excel", e);
        }
    }

    private Transaction parseRow(Row row, DataFormatter formatter, int dateColIdx, int descColIdx,
                                 int amountColIdx, User user, UUID householdId) {
        String rawDate = cellText(row, dateColIdx, formatter);
        String rawAmount = cellText(row, amountColIdx, formatter);
        String description = descColIdx >= 0 ? cellText(row, descColIdx, formatter) : null;

        LocalDate date = parseDate(rawDate);
        BigDecimal signedAmount = parseAmount(rawAmount);

        // El signo del importe determina el tipo: es la convención que
        // usan prácticamente todos los bancos (negativo = cargo/gasto).
        // No usamos mapping.typeColumn() todavía -- cada banco nombra esa
        // columna de forma distinta ("Cargo/Abono", "Débito/Crédito"...)
        // y el signo es una señal más fiable y universal.
        String type = signedAmount.signum() < 0 ? "expense" : "income";
        BigDecimal positiveAmount = signedAmount.abs();

        Transaction transaction = new Transaction(user, type, positiveAmount, date);
        transaction.setDescription(description);
        transaction.setHouseholdId(householdId);
        return transaction;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RowParseException("fecha vacía");
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), fmt);
            } catch (DateTimeParseException ignored) {
                // probamos el siguiente formato
            }
        }
        throw new RowParseException("fecha no reconocida: '" + raw + "'");
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RowParseException("importe vacío");
        }
        // Normaliza formatos "1.234,56" (español) y "1,234.56" (inglés)
        // a un BigDecimal válido: quita separadores de miles y convierte
        // la coma decimal a punto si hace falta.
        String cleaned = raw.trim().replace(" ", "").replace("€", "");
        boolean hasComma = cleaned.contains(",");
        boolean hasDot = cleaned.contains(".");
        if (hasComma && hasDot) {
            // El último separador es el decimal; el otro es de miles.
            cleaned = cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
                    ? cleaned.replace(".", "").replace(",", ".")
                    : cleaned.replace(",", "");
        } else if (hasComma) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new RowParseException("importe no reconocido: '" + raw + "'");
        }
    }

    private int findColumnIndex(Row headerRow, String columnName) {
        if (columnName == null) return -1;
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            if (formatter.formatCellValue(cell).trim().equals(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String cellText(Row row, int colIdx, DataFormatter formatter) {
        if (colIdx < 0) return null;
        Cell cell = row.getCell(colIdx);
        return cell != null ? formatter.formatCellValue(cell).trim() : null;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    /** Excepción interna: una fila concreta no se pudo interpretar. No es un error fatal para toda la importación. */
    private static class RowParseException extends RuntimeException {
        RowParseException(String message) { super(message); }
    }
}