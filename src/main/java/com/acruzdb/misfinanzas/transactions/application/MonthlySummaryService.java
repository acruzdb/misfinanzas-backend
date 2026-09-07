package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.transactions.dto.MonthlySummaryResponse;
import com.acruzdb.misfinanzas.transactions.dto.MonthlySummaryResponse.CategoryBreakdown;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcula el resumen financiero mensual de un usuario, agregando datos
 * de {@code transactions} sin necesidad de guardar totales precalculados.
 */
@Service
public class MonthlySummaryService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public MonthlySummaryService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Genera el resumen de un usuario para el mes indicado.
     *
     * @param userId id del usuario
     * @param month  mes a resumir
     * @return el resumen completo: totales, variación vs. mes anterior,
     *         ahorro acumulado y desglose por categoría
     */
    @Transactional(readOnly = true)
    public MonthlySummaryResponse getSummary(UUID userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        BigDecimal income = transactionRepository.sumByUserTypeAndDateRange(userId, "income", start, end);
        BigDecimal expense = transactionRepository.sumByUserTypeAndDateRange(userId, "expense", start, end);
        BigDecimal netSavings = income.subtract(expense);

        BigDecimal changePercent = calculateChangeVsPreviousMonth(userId, month, netSavings);
        BigDecimal totalSavedAllTime = transactionRepository.sumNetAllTimeForUser(userId);
        List<CategoryBreakdown> breakdown = buildBreakdown(userId, start, end);

        return new MonthlySummaryResponse(month, income, expense, netSavings, changePercent, totalSavedAllTime, breakdown);
    }

    /**
     * Calcula la variación porcentual del ahorro neto respecto al mes anterior.
     * <p>
     * Devuelve {@code null} en vez de lanzar una división por cero cuando
     * el mes anterior tuvo ahorro neto exactamente cero — en ese caso el
     * porcentaje de variación no tiene un valor matemáticamente sensato
     * que mostrar, y es mejor que el frontend decida cómo representar
     * "sin dato" (por ejemplo, ocultando la flecha de tendencia) en vez
     * de recibir un número engañoso.
     *
     * @param userId            id del usuario
     * @param month             mes actual
     * @param currentNetSavings ahorro neto ya calculado del mes actual
     * @return variación porcentual, o {@code null} si no es calculable
     */
    private BigDecimal calculateChangeVsPreviousMonth(UUID userId, YearMonth month, BigDecimal currentNetSavings) {
        YearMonth previousMonth = month.minusMonths(1);
        BigDecimal previousIncome = transactionRepository.sumByUserTypeAndDateRange(
                userId, "income", previousMonth.atDay(1), previousMonth.atEndOfMonth());
        BigDecimal previousExpense = transactionRepository.sumByUserTypeAndDateRange(
                userId, "expense", previousMonth.atDay(1), previousMonth.atEndOfMonth());
        BigDecimal previousNetSavings = previousIncome.subtract(previousExpense);

        if (previousNetSavings.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return currentNetSavings.subtract(previousNetSavings)
                .divide(previousNetSavings.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Construye el desglose de gastos por categoría, resolviendo nombre
     * y color a partir de los ids agrupados por la consulta.
     *
     * @param userId id del usuario
     * @param start  inicio del rango de fechas
     * @param end    fin del rango de fechas
     * @return desglose legible, uno por categoría con gasto en el mes
     */
    private List<CategoryBreakdown> buildBreakdown(UUID userId, LocalDate start, LocalDate end) {
        List<Object[]> rows = transactionRepository.sumExpensesByCategoryForUser(userId, start, end);
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> categoryIds = rows.stream()
                .map(row -> (UUID) row[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Category> categoriesById = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        return rows.stream().map(row -> {
            UUID categoryId = (UUID) row[0];
            BigDecimal amount = (BigDecimal) row[1];

            if (categoryId == null) {
                return new CategoryBreakdown(null, "Sin categoría", "#6B7280", amount);
            }
            Category category = categoriesById.get(categoryId);
            // La categoría podría haber sido borrada después de que el
            // movimiento se creara con ella; el movimiento sigue existiendo
            // (categoryId se pone a NULL por ON DELETE SET NULL en la BD,
            // así que en la práctica esta rama solo cubre una carrera muy
            // estrecha, pero es más seguro no asumir que siempre existe).
            String name = category != null ? category.getName() : "Categoría eliminada";
            String color = category != null ? category.getColorHex() : "#6B7280";
            return new CategoryBreakdown(categoryId, name, color, amount);
        }).toList();
    }
}