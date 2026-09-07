package com.acruzdb.misfinanzas.transactions.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumen financiero de un usuario para un mes concreto.
 *
 * @param month                                mes al que corresponde el resumen
 * @param totalIncome                          ingresos del mes
 * @param totalExpense                         gastos del mes
 * @param netSavings                           ingresos menos gastos del mes
 * @param savingsChangePercentVsPreviousMonth  variación del ahorro neto vs. mes anterior; null si no es calculable
 * @param incomeChangePercentVsPreviousMonth   variación de ingresos vs. mes anterior; null si no es calculable
 * @param expenseChangePercentVsPreviousMonth  variación de gastos vs. mes anterior; null si no es calculable
 * @param totalSavedAllTime                    ahorro neto acumulado desde el primer movimiento
 * @param totalSavedChangePercentVsPreviousMonth variación del acumulado respecto al cierre del mes anterior; null si no es calculable
 * @param expensesByCategory                   desglose de gastos del mes por categoría
 */
public record MonthlySummaryResponse(
        YearMonth month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netSavings,
        BigDecimal savingsChangePercentVsPreviousMonth,
        BigDecimal incomeChangePercentVsPreviousMonth,
        BigDecimal expenseChangePercentVsPreviousMonth,
        BigDecimal totalSavedAllTime,
        BigDecimal totalSavedChangePercentVsPreviousMonth,
        List<CategoryBreakdown> expensesByCategory
) {
    public record CategoryBreakdown(UUID categoryId, String categoryName, String colorHex, BigDecimal amount) {}
}