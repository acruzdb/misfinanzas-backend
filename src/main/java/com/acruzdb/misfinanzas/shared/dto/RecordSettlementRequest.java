package com.acruzdb.misfinanzas.shared.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Petición para registrar una liquidación entre dos miembros del household.
 * <p>
 * Se piden explícitamente {@code fromUserId} (quien pagó en la vida real)
 * y {@code toUserId} (quien recibió), en vez de asumir siempre que quien
 * hace la petición es el pagador — la persona autenticada puede estar
 * registrando que le pagaron A ELLA, no solo que ella pagó.
 *
 * @param fromUserId quien pagó
 * @param toUserId   quien recibió el pago
 * @param amount     importe liquidado
 */
public record RecordSettlementRequest(
        @NotNull UUID fromUserId,
        @NotNull UUID toUserId,
        @NotNull @DecimalMin(value = "0.01", message = "El importe debe ser mayor que 0") BigDecimal amount
) {}