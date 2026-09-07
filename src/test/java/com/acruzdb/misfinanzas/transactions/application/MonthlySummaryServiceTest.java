package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.transactions.dto.MonthlySummaryResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlySummaryServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;

    private MonthlySummaryService service;

    @Test
    @DisplayName("getSummary() calcula el ahorro neto y las variaciones vs. mes anterior")
    void getSummary_calculaTotalesYVariaciones() {
        service = new MonthlySummaryService(transactionRepository, categoryRepository);
        UUID userId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);

        // Mes actual: 2450 ingresos, 1680 gastos. Mes anterior: 2000 / 2000.
        when(transactionRepository.sumByUserTypeAndDateRange(eq(userId), eq("income"), any(), any()))
                .thenReturn(new BigDecimal("2450.00"), new BigDecimal("2000.00"));
        when(transactionRepository.sumByUserTypeAndDateRange(eq(userId), eq("expense"), any(), any()))
                .thenReturn(new BigDecimal("1680.00"), new BigDecimal("2000.00"));
        when(transactionRepository.sumNetAllTimeForUser(userId)).thenReturn(new BigDecimal("8320.00"));
        when(transactionRepository.sumExpensesByCategoryForUser(eq(userId), any(), any())).thenReturn(List.of());

        MonthlySummaryResponse response = service.getSummary(userId, month);

        assertThat(response.netSavings()).isEqualByComparingTo("770.00");
        assertThat(response.totalSavedAllTime()).isEqualByComparingTo("8320.00");
        // Ingresos: (2450-2000)/2000 * 100 = 22.5%
        assertThat(response.incomeChangePercentVsPreviousMonth()).isEqualByComparingTo("22.5000");
        // Gastos: (1680-2000)/2000 * 100 = -16%
        assertThat(response.expenseChangePercentVsPreviousMonth()).isEqualByComparingTo("-16.0000");
    }

    @Test
    @DisplayName("getSummary() devuelve null en variaciones cuyo valor de comparación es cero")
    void getSummary_devuelveNullSiMesAnteriorEsCero() {
        service = new MonthlySummaryService(transactionRepository, categoryRepository);
        UUID userId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);

        when(transactionRepository.sumByUserTypeAndDateRange(eq(userId), eq("income"), any(), any()))
                .thenReturn(new BigDecimal("500.00"), BigDecimal.ZERO);
        when(transactionRepository.sumByUserTypeAndDateRange(eq(userId), eq("expense"), any(), any()))
                .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
        when(transactionRepository.sumNetAllTimeForUser(userId)).thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.sumExpensesByCategoryForUser(eq(userId), any(), any())).thenReturn(List.of());

        MonthlySummaryResponse response = service.getSummary(userId, month);

        assertThat(response.incomeChangePercentVsPreviousMonth()).isNull();
        assertThat(response.savingsChangePercentVsPreviousMonth()).isNull();
    }

    @Test
    @DisplayName("getSummary() resuelve nombre y color de cada categoría en el desglose")
    void getSummary_resuelveDesglosePorCategoria() throws Exception {
        service = new MonthlySummaryService(transactionRepository, categoryRepository);
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);

        Category comida = new Category((com.acruzdb.misfinanzas.auth.domain.User) null, "Comida", "expense");
        setId(comida, categoryId);

        when(transactionRepository.sumByUserTypeAndDateRange(eq(userId), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumNetAllTimeForUser(userId)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumExpensesByCategoryForUser(eq(userId), any(), any()))
                .thenReturn(Collections.singletonList(new Object[]{categoryId, new BigDecimal("120.00")}));
        when(categoryRepository.findAllById(Set.of(categoryId))).thenReturn(List.of(comida));

        MonthlySummaryResponse response = service.getSummary(userId, month);

        assertThat(response.expensesByCategory()).hasSize(1);
        assertThat(response.expensesByCategory().get(0).categoryName()).isEqualTo("Comida");
    }

    private void setId(Object entity, UUID id) throws Exception {
        java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}