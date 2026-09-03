package com.acruzdb.misfinanzas.transactions.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos de entrada para crear un movimiento.
 *
 * @param type            {@code "income"} o {@code "expense"}
 * @param amount          importe, siempre positivo
 * @param transactionDate fecha del movimiento, no puede ser futura
 * @param description     descripción libre, opcional
 * @param categoryId      categoría asociada, opcional
 * @param householdId     household al que pertenece el movimiento;
 *                         {@code null} significa movimiento personal
 */
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

        UUID categoryId,

        UUID householdId
) {}