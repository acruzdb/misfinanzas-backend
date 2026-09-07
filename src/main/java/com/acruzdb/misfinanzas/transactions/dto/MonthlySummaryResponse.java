package com.acruzdb.misfinanzas.transactions.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumen financiero de un usuario para un mes concreto, pensado para
 * alimentar directamente las tarjetas del dashboard.
 *
 * @param month                              mes al que corresponde el resumen
 * @param totalIncome                        ingresos del mes
 * @param totalExpense                       gastos del mes
 * @param netSavings                         ingresos menos gastos del mes (puede ser negativo)
 * @param savingsChangePercentVsPreviousMonth variación porcentual del ahorro neto
 *                                            respecto al mes anterior; {@code null} si el
 *                                            mes anterior no tiene datos con los que comparar
 * @param totalSavedAllTime                  ahorro neto acumulado desde el primer movimiento
 * @param expensesByCategory                 desglose de gastos del mes por categoría
 */
public record MonthlySummaryResponse(
        YearMonth month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netSavings,
        BigDecimal savingsChangePercentVsPreviousMonth,
        BigDecimal totalSavedAllTime,
        List<CategoryBreakdown> expensesByCategory
) {
    /**
     * Gasto total de una categoría dentro del mes consultado.
     *
     * @param categoryId  id de la categoría; {@code null} si el gasto no tiene categoría
     * @param categoryName nombre visible, o un texto descriptivo si la categoría ya no existe
     * @param colorHex    color para pintar en el gráfico
     * @param amount      total gastado en esa categoría durante el mes
     */
    public record CategoryBreakdown(UUID categoryId, String categoryName, String colorHex, BigDecimal amount) {}
}