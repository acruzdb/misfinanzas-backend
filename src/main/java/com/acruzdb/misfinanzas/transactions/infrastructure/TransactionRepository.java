package com.acruzdb.misfinanzas.transactions.infrastructure;

import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Spring Data genera la consulta a partir del nombre del método —
    // no hace falta escribir JPQL para casos simples como este.
    List<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId);

    // Para casos algo más específicos (aquí: solo movimientos personales,
    // es decir sin household_id), usamos @Query explícita.
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.householdId IS NULL ORDER BY t.transactionDate DESC")
    List<Transaction> findPersonalByUserId(@Param("userId") UUID userId);

    /**
     * Suma los movimientos personales de un usuario de un tipo concreto
     * (ingreso o gasto) dentro de un rango de fechas.
     *
     * @param userId id del usuario
     * @param type   {@code "income"} o {@code "expense"}
     * @param start  fecha de inicio del rango (inclusive)
     * @param end    fecha de fin del rango (inclusive)
     * @return la suma, o cero si no hay movimientos que coincidan
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.householdId IS NULL " +
            "AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal sumByUserTypeAndDateRange(@Param("userId") UUID userId, @Param("type") String type,
                                         @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Calcula el ahorro neto acumulado de un usuario a lo largo de toda su
     * historia (todos los ingresos personales menos todos los gastos
     * personales, sin límite de fechas).
     *
     * @param userId id del usuario
     * @return ingresos menos gastos, acumulado; puede ser negativo
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE -t.amount END), 0) " +
            "FROM Transaction t WHERE t.user.id = :userId AND t.householdId IS NULL")
    BigDecimal sumNetAllTimeForUser(@Param("userId") UUID userId);

    /**
     * Agrupa los gastos personales de un usuario por categoría, dentro de
     * un rango de fechas.
     * <p>
     * Devuelve pares {@code [categoryId, totalGastado]} en bruto porque
     * JPQL no permite proyectar directamente a un record con una relación
     * agrupada de esta forma; el mapeo a un DTO legible se hace en el
     * servicio (ver {@code MonthlySummaryService#buildBreakdown}).
     *
     * @param userId id del usuario
     * @param start  fecha de inicio del rango (inclusive)
     * @param end    fecha de fin del rango (inclusive)
     * @return lista de {@code Object[]{categoryId, total}}, un par por categoría
     */
    @Query("SELECT t.categoryId, COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = 'expense' AND t.householdId IS NULL " +
            "AND t.transactionDate BETWEEN :start AND :end GROUP BY t.categoryId")
    List<Object[]> sumExpensesByCategoryForUser(@Param("userId") UUID userId,
                                                @Param("start") LocalDate start, @Param("end") LocalDate end);
}