package com.acruzdb.misfinanzas.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea la configuración {@code misfinanzas.security.jwt.*} del
 * {@code application.yml} a un objeto tipado, en vez de leer cada
 * valor suelto con {@code @Value}.
 *
 * @param secret              clave usada para firmar los JWT; en producción
 *                            debe venir de una variable de entorno, nunca
 *                            hardcodeada en el YAML
 * @param accessTokenMinutes  minutos de validez del access token
 * @param refreshTokenDays    días de validez del refresh token
 */
@ConfigurationProperties(prefix = "misfinanzas.security.jwt")
public record JwtProperties(String secret, int accessTokenMinutes, int refreshTokenDays) {
}