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
 * @param currency         moneda en formato ISO 4217, p.ej. {@code "EUR"}
 * @param description      descripción libre, puede ser null
 * @param transactionDate  fecha del movimiento
 * @param categoryId       categoría asociada, puede ser null
 * @param householdId      household al que pertenece; null si es personal
 */
public record TransactionResponse(
        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String description,
        LocalDate transactionDate,
        UUID categoryId,
        UUID householdId
) {
    // Factory method: así el mapeo Entidad -> DTO vive junto al propio DTO,
    // no disperso dentro del servicio.
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getAmount(), t.getCurrency(),
                t.getDescription(), t.getTransactionDate(), t.getCategoryId(), t.getHouseholdId()
        );
    }
}