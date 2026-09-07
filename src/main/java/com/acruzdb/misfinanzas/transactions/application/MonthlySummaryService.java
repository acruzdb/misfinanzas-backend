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
     * @return el resumen completo: totales, variaciones vs. mes anterior,
     *         ahorro acumulado y desglose por categoría
     */
    @Transactional(readOnly = true)
    public MonthlySummaryResponse getSummary(UUID userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        BigDecimal income = transactionRepository.sumByUserTypeAndDateRange(userId, "income", start, end);
        BigDecimal expense = transactionRepository.sumByUserTypeAndDateRange(userId, "expense", start, end);
        BigDecimal netSavings = income.subtract(expense);
        BigDecimal totalSavedAllTime = transactionRepository.sumNetAllTimeForUser(userId);

        YearMonth previousMonth = month.minusMonths(1);
        BigDecimal previousIncome = transactionRepository.sumByUserTypeAndDateRange(
                userId, "income", previousMonth.atDay(1), previousMonth.atEndOfMonth());
        BigDecimal previousExpense = transactionRepository.sumByUserTypeAndDateRange(
                userId, "expense", previousMonth.atDay(1), previousMonth.atEndOfMonth());
        BigDecimal previousNetSavings = previousIncome.subtract(previousExpense);

        // El acumulado "antes de este mes" se deriva del acumulado total menos
        // el neto de este mes, en vez de lanzar otra consulta a la base de
        // datos -- ya tenemos todo lo necesario en memoria.
        BigDecimal previousTotalSaved = totalSavedAllTime.subtract(netSavings);

        BigDecimal incomeChange = percentChange(income, previousIncome);
        BigDecimal expenseChange = percentChange(expense, previousExpense);
        BigDecimal savingsChange = percentChange(netSavings, previousNetSavings);
        BigDecimal totalSavedChange = percentChange(totalSavedAllTime, previousTotalSaved);

        List<CategoryBreakdown> breakdown = buildBreakdown(userId, start, end);

        return new MonthlySummaryResponse(
                month, income, expense, netSavings, savingsChange,
                incomeChange, expenseChange, totalSavedAllTime, totalSavedChange, breakdown
        );
    }

    /**
     * Calcula la variación porcentual entre dos valores.
     * <p>
     * Devuelve {@code null} en vez de dividir por cero cuando el valor
     * anterior es exactamente cero — no hay un porcentaje de variación
     * matemáticamente sensato en ese caso, y es mejor que el frontend
     * decida cómo representar "sin dato" que recibir un número engañoso.
     *
     * @param current  valor del periodo actual
     * @param previous valor del periodo de comparación
     * @return variación porcentual, o {@code null} si no es calculable
     */
    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

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
            String name = category != null ? category.getName() : "Categoría eliminada";
            String color = category != null ? category.getColorHex() : "#6B7280";
            return new CategoryBreakdown(categoryId, name, color, amount);
        }).toList();
    }
}