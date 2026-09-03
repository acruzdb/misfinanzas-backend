package com.acruzdb.misfinanzas.transactions.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(

        @NotNull(message = "El tipo es obligatorio")
        @Pattern(regexp = "income|expense", message = "El tipo debe ser 'income' o 'expense'")
        String type,

        @NotNull(message = "El importe es obligatorio")
        @DecimalMin(value = "0.01", message = "El importe debe ser mayor que 0")
        BigDecimal amount,

        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate transactionDate,

        String description,

        UUID categoryId
) {}