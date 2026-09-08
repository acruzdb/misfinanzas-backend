package com.acruzdb.misfinanzas.transactions.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parte de un reparto personalizado indicada por el usuario al crear
 * un gasto compartido.
 *
 * @param userId       participante al que le corresponde esta parte
 * @param shareAmount  importe que le toca pagar
 */
public record SplitInput(UUID userId, BigDecimal shareAmount) {}