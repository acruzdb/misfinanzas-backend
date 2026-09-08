package com.acruzdb.misfinanzas.transactions.infrastructure;

import com.acruzdb.misfinanzas.transactions.domain.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos de {@link ExpenseSplit}.
 */
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    /** @return los repartos de un movimiento concreto */
    List<ExpenseSplit> findByTransactionId(UUID transactionId);

    /**
     * Todos los repartos de gastos de un household, usados para
     * calcular los balances netos de cada miembro.
     */
    @Query("SELECT s FROM ExpenseSplit s WHERE s.transaction.householdId = :householdId")
    List<ExpenseSplit> findByHouseholdId(@Param("householdId") UUID householdId);
}