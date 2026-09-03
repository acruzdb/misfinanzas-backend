package com.acruzdb.misfinanzas.auth.infrastructure;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtro que se ejecuta en cada petición HTTP: si viene un header
 * {@code Authorization: Bearer <token>} válido, autentica al usuario
 * en el {@link SecurityContextHolder} para el resto de la petición.
 * <p>
 * Si no hay header, o el token es inválido/expirado, simplemente deja
 * pasar la petición sin autenticar — es {SecurityConfig} quien
 * decide después si esa ruta requiere autenticación o no.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                UUID userId = jwtService.validateAndGetUserId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Token inválido, expirado o mal formado: no autenticamos.
                // No lanzamos aquí — dejamos que SecurityConfig devuelva 401
                // si la ruta solicitada requiere autenticación.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}