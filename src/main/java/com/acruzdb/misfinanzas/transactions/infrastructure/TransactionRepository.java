package com.acruzdb.misfinanzas.transactions.infrastructure;

import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}