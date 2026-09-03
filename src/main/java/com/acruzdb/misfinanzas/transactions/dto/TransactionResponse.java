package com.acruzdb.misfinanzas.transactions.dto;

import com.acruzdb.misfinanzas.transactions.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String description,
        LocalDate transactionDate,
        UUID categoryId
) {
    // Factory method: así el mapeo Entidad -> DTO vive junto al propio DTO,
    // no disperso dentro del servicio.
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getAmount(), t.getCurrency(),
                t.getDescription(), t.getTransactionDate(), t.getCategoryId()
        );
    }
}