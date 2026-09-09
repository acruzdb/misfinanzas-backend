package com.acruzdb.misfinanzas.statements.dto;

import com.acruzdb.misfinanzas.statements.domain.StatementImport;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resumen de una importación ya ejecutada, para el historial que ve
 * el usuario -- no incluye el fichero original (nunca lo guardamos,
 * ver nota de alcance en el módulo), solo el resultado.
 */
public record StatementImportSummary(
        UUID id,
        String originalFilename,
        String status,
        Integer rowsTotal,
        Integer rowsImported,
        Integer rowsFailed,
        OffsetDateTime createdAt
) {
    public static StatementImportSummary from(StatementImport entity) {
        return new StatementImportSummary(
                entity.getId(), entity.getOriginalFilename(), entity.getStatus(),
                entity.getRowsTotal(), entity.getRowsImported(), entity.getRowsFailed(), entity.getCreatedAt()
        );
    }
}