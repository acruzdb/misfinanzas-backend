package com.acruzdb.misfinanzas.statements.dto;

import com.acruzdb.misfinanzas.statements.domain.StatementImport;
import java.util.UUID;

/**
 * Resultado de una importación ya ejecutada.
 *
 * @param id            id del registro de importación
 * @param status        {@code "completed"} (de momento es el único estado
 *                      posible synchrono; no hay procesamiento en segundo plano todavía)
 * @param rowsTotal     filas de datos encontradas en el fichero
 * @param rowsImported  filas que se convirtieron en movimientos con éxito
 * @param rowsFailed    filas que no se pudieron interpretar (fecha o importe inválidos)
 * @param errorSummary  resumen de los primeros errores, o null si no hubo ninguno
 */
public record StatementImportResponse(
        UUID id, String status, int rowsTotal, int rowsImported, int rowsFailed, String errorSummary
) {
    public static StatementImportResponse from(StatementImport entity) {
        return new StatementImportResponse(
                entity.getId(), entity.getStatus(), entity.getRowsTotal(),
                entity.getRowsImported(), entity.getRowsFailed(), entity.getErrorSummary()
        );
    }
}