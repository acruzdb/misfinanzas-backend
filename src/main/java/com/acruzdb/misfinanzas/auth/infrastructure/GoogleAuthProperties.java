package com.acruzdb.misfinanzas.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea la configuración {@code misfinanzas.security.google.*}.
 *
 * @param clientId Client ID de OAuth creado en Google Cloud Console,
 *                 usado para validar que los ID tokens que llegan
 *                 realmente fueron emitidos para nuestra aplicación
 */
@ConfigurationProperties(prefix = "misfinanzas.security.google")
public record GoogleAuthProperties(String clientId) {
}