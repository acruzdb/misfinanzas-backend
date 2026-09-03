package com.acruzdb.misfinanzas.auth.infrastructure;

import java.util.UUID;

/**
 * Representa al usuario ya autenticado dentro de una petición HTTP.
 * <p>
 * Es el "principal" que colocamos en el {@code SecurityContext} tras
 * validar el JWT (ver {JwtAuthFilter}). Usar un tipo propio en
 * vez de un {@code String} suelto con el id evita parsear UUIDs a mano
 * en cada controller.
 *
 * @param id id del usuario autenticado
 */
public record AuthenticatedUser(UUID id) {}