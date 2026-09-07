package com.acruzdb.misfinanzas.statements.dto;

import java.util.List;

/**
 * Resultado de analizar un fichero recién subido, antes de importar nada.
 * <p>
 * Permite al frontend mostrar una vista previa y, si la detección
 * automática falla, dejar que el usuario corrija el mapeo antes de
 * confirmar la importación real.
 *
 * @param headers           cabeceras detectadas en la primera fila del fichero
 * @param sampleRows         primeras filas de datos, ya como texto plano, para previsualizar
 * @param suggestedMapping   mapeo que la app cree adecuado, basado en nombres de cabecera habituales;
 *                           puede tener campos a null si no reconoció alguna columna
 */
public record StatementPreviewResponse(List<String> headers, List<List<String>> sampleRows, ColumnMapping suggestedMapping) {}