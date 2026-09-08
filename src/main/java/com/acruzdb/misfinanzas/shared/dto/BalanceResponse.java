package com.acruzdb.misfinanzas.shared.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Balances netos de un household y las transferencias mínimas
 * sugeridas para saldar todas las deudas entre sus miembros.
 *
 * @param balances             balance neto de cada miembro (positivo =
 *                             le deben; negativo = debe)
 * @param suggestedSettlements transferencias mínimas para saldar cuentas,
 *                             calculadas con un algoritmo voraz de
 *                             simplificación de deudas
 */
public record BalanceResponse(List<MemberBalance> balances, List<SuggestedSettlement> suggestedSettlements) {

    public record MemberBalance(UUID userId, String displayName, BigDecimal netBalance) {}

    public record SuggestedSettlement(UUID fromUserId, String fromName, UUID toUserId, String toName, BigDecimal amount) {}
}