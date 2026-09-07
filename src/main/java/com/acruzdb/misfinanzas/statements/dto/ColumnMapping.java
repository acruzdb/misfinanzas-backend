package com.acruzdb.misfinanzas.statements.dto;

/**
 * Indica qué columna del fichero subido corresponde a cada campo de un
 * movimiento. Los valores son los nombres de cabecera tal cual
 * aparecen en la fila de encabezado del Excel (no índices numéricos,
 * para que el mapeo siga siendo válido aunque el usuario reordene
 * columnas entre importaciones del mismo banco).
 *
 * @param dateColumn        cabecera de la columna de fecha
 * @param descriptionColumn cabecera de la columna de descripción/concepto
 * @param amountColumn      cabecera de la columna de importe
 * @param typeColumn        cabecera de la columna de tipo (ingreso/gasto),
 *                          opcional — si es null, el tipo se infiere del
 *                          signo del importe (negativo = gasto)
 */
public record ColumnMapping(String dateColumn, String descriptionColumn, String amountColumn, String typeColumn) {}