package com.acruzdb.misfinanzas.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea {@code misfinanzas.security.cors.*}.
 *
 * @param allowedOrigin origen del frontend autorizado a llamar a esta
 *                       API desde el navegador; distinto en desarrollo
 *                       (localhost) y producción (dominio real)
 */
@ConfigurationProperties(prefix = "misfinanzas.security.cors")
public record CorsProperties(String allowedOrigin) {}