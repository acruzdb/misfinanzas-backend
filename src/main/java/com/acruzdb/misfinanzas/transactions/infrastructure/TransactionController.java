package com.acruzdb.misfinanzas.transactions.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.transactions.application.TransactionService;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * API REST del módulo de movimientos (ingresos y gastos).
 * <p>
 * TEMPORAL: el usuario se identifica por un parámetro {@code userId} en
 * la URL, porque todavía no existe autenticación (Paso 3). En cuanto
 * montemos el login JWT, este parámetro desaparece de todos los
 * endpoints y el usuario se extrae del token, nunca de algo que el
 * cliente pueda falsear libremente.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    public TransactionController(TransactionService transactionService, UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    /**
     * Crea un nuevo movimiento para el usuario indicado.
     *
     * @param principal  id del usuario propietario (temporal, ver nota de clase)
     * @param request datos del movimiento, validados con Bean Validation
     * @return 201 Created con el movimiento creado
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTransactionRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        TransactionResponse response = transactionService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista los movimientos del usuario, del más reciente al más antiguo.
     *
     * @param principal id del usuario (temporal, ver nota de clase)
     * @return 200 OK con la lista de movimientos
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(transactionService.listForUser(principal.id()));
    }

    /**
     * Recupera un movimiento concreto por id.
     *
     * @param id      id del movimiento
     * @param principal  id del usuario solicitante (temporal, ver nota de clase)
     * @return 200 OK con el movimiento, o 404 si no existe / no es suyo
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(transactionService.getById(id, principal.id()));
    }

    /**
     * Elimina un movimiento.
     *
     * @param id      id del movimiento a borrar
     * @param principal  id del usuario solicitante (temporal, ver nota de clase)
     * @return 204 No Content si se borró correctamente
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        transactionService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }
}