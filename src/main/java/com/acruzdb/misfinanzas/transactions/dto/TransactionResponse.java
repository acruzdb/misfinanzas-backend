package com.acruzdb.misfinanzas.transactions.dto;

import com.acruzdb.misfinanzas.transactions.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representación de un movimiento devuelta por la API.
 *
 * @param id               identificador del movimiento
 * @param type             {@code "income"} o {@code "expense"}
 * @param amount           importe, siempre positivo
 * @param currency         moneda en formato ISO 4217
 * @param description      descripción libre, puede ser null
 * @param transactionDate  fecha del movimiento
 * @param categoryId       categoría asociada, puede ser null
 * @param householdId      household al que pertenece; null si es personal
 * @param paidByUserId     id de quien dio de alta el movimiento
 * @param paidByName       nombre visible de quien lo dio de alta —
 *                         relevante sobre todo en la vista de gastos
 *                         compartidos, para saber quién pagó qué
 */
public record TransactionResponse(
        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String description,
        LocalDate transactionDate,
        UUID categoryId,
        UUID householdId,
        UUID paidByUserId,
        String paidByName
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getAmount(), t.getCurrency(),
                t.getDescription(), t.getTransactionDate(), t.getCategoryId(), t.getHouseholdId(),
                t.getUser().getId(), t.getUser().getDisplayName()
        );
    }
}