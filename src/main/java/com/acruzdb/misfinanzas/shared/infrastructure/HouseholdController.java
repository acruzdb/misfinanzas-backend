package com.acruzdb.misfinanzas.shared.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.application.BalanceService;
import com.acruzdb.misfinanzas.shared.application.HouseholdService;
import com.acruzdb.misfinanzas.shared.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * API REST del módulo de grupos compartidos (households).
 */
@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    private final HouseholdService householdService;
    private final UserRepository userRepository;
    private final BalanceService balanceService;

    public HouseholdController(HouseholdService householdService, UserRepository userRepository, BalanceService balanceService) {
        this.householdService = householdService;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
    }

    /** Crea un nuevo household; el usuario autenticado queda como owner. */
    @PostMapping
    public ResponseEntity<HouseholdResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateHouseholdRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        HouseholdResponse response = householdService.create(user, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista los households a los que pertenece el usuario autenticado. */
    @GetMapping
    public ResponseEntity<List<HouseholdResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(householdService.listForUser(principal.id()));
    }

    /** Detalle de un household, con sus miembros. */
    @GetMapping("/{id}")
    public ResponseEntity<HouseholdResponse> getById(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(householdService.getById(id, principal.id()));
    }

    /** Añade un usuario existente al household (solo el owner puede hacerlo). */
    @PostMapping("/{id}/members")
    public ResponseEntity<HouseholdResponse> addMember(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.ok(householdService.addMember(id, principal.id(), request.email()));
    }

    /** El usuario autenticado abandona el household. */
    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<Void> leave(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        householdService.leave(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    /** Balances netos del household y transferencias sugeridas para saldarlos. */
    @GetMapping("/{id}/balances")
    public ResponseEntity<BalanceResponse> balances(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(balanceService.computeBalances(id, principal.id()));
    }

    /** Registra una liquidación real entre dos miembros del household (el solicitante debe ser una de las dos partes). */
    @PostMapping("/{id}/settlements")
    public ResponseEntity<Void> recordSettlement(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RecordSettlementRequest request) {
        balanceService.recordSettlement(id, principal.id(), request.fromUserId(), request.toUserId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}