package com.acruzdb.misfinanzas.statements.dto;

import java.util.UUID;

/**
 * Datos que acompañan al fichero en la petición de importación real,
 * enviados como una parte JSON separada de la petición multipart
 * (ver {@code StatementController#confirmImport}).
 *
 * @param mapping     mapeo de columnas ya confirmado por el usuario
 *                    (el que sugirió {@code /preview}, o uno corregido a mano)
 * @param householdId si se indica, los movimientos se crean como compartidos
 *                    en ese household; si es null, son personales
 */
public record ConfirmImportRequest(ColumnMapping mapping, UUID householdId) {}