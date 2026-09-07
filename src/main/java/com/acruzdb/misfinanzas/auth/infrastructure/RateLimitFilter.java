package com.acruzdb.misfinanzas.auth.infrastructure;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limita los intentos a los endpoints públicos de autenticación por IP,
 * para dificultar ataques de fuerza bruta o abuso automatizado.
 * <p>
 * Implementación en memoria (un bucket por IP): suficiente para el
 * volumen de este proyecto. Si el número de usuarios creciera mucho,
 * o hubiera varias instancias del backend detrás de un balanceador,
 * habría que migrar el estado a Redis para que el límite sea
 * compartido entre instancias.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isProtectedAuthEndpoint(request)) {
            Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("Demasiados intentos, inténtalo de nuevo en un minuto");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedAuthEndpoint(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/auth/google") || path.equals("/api/auth/refresh");
    }

    private Bucket newBucket() {
        // 10 intentos por minuto por IP: generoso para un usuario legítimo
        // que falla el login un par de veces, restrictivo para un bot.
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}