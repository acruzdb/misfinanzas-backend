package com.acruzdb.misfinanzas.shared.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Petición para registrar que el usuario autenticado ya le pagó a
 * otro miembro del household una cantidad concreta.
 *
 * @param toUserId  quien recibió el pago
 * @param amount    importe liquidado
 */
public record RecordSettlementRequest(
        @NotNull UUID toUserId,
        @NotNull @DecimalMin(value = "0.01", message = "El importe debe ser mayor que 0") BigDecimal amount
) {}