package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio del módulo de movimientos.
 * <p>
 * Aplica siempre la comprobación de propiedad: un usuario solo puede
 * leer, listar o borrar sus propios movimientos, nunca los de otro.
 * Además, valida que la categoría (si la hay) y el household (si el
 * movimiento es compartido) sean realmente accesibles para el usuario
 * antes de guardar el movimiento.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              HouseholdMemberRepository householdMemberRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    /**
     * Da de alta un nuevo movimiento para el usuario indicado.
     *
     * @param user     usuario propietario del movimiento (ya cargado de BD)
     * @param request  datos validados del movimiento a crear
     * @return el movimiento creado, ya con su id asignado
     * @throws ResponseStatusException 400 si se indica un {@code categoryId}
     *         que no existe o no es visible para el usuario; 403 si se
     *         indica un {@code householdId} del que el usuario no es miembro
     */
    @Transactional
    public TransactionResponse create(User user, CreateTransactionRequest request) {
        if (request.categoryId() != null) {
            validateCategoryAccess(request.categoryId(), user.getId());
        }
        if (request.householdId() != null) {
            validateHouseholdMembership(request.householdId(), user.getId());
        }

        Transaction transaction = new Transaction(
                user, request.type(), request.amount(), request.transactionDate()
        );
        transaction.setDescription(request.description());
        transaction.setCategoryId(request.categoryId());
        transaction.setHouseholdId(request.householdId());

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    /**
     * Comprueba que una categoría exista y sea visible para el usuario
     * (propia o de sistema) antes de permitir asociarla a un movimiento.
     *
     * @param categoryId id de la categoría a validar
     * @param userId     id del usuario que está creando el movimiento
     * @throws ResponseStatusException 400 si no existe o no le pertenece
     */
    private void validateCategoryAccess(UUID categoryId, UUID userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no existe"));

        boolean visible = category.isSystem() || category.belongsTo(userId);
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no es válida para este usuario");
        }
    }

    /**
     * Comprueba que el usuario sea miembro del household indicado antes
     * de permitir registrar un movimiento compartido en él.
     * <p>
     * Se usa 403 (no 400) a diferencia de la validación de categoría:
     * aquí el household sí podría existir perfectamente, el problema es
     * que el usuario no pertenece a él — es una cuestión de autorización,
     * no de dato mal formado.
     *
     * @param householdId id del household a validar
     * @param userId      id del usuario que está creando el movimiento
     * @throws ResponseStatusException 403 si el usuario no es miembro
     */
    private void validateHouseholdMembership(UUID householdId, UUID userId) {
        boolean isMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId).isPresent();
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a ese household");
        }
    }

    /**
     * Lista los movimientos del usuario, del más reciente al más antiguo.
     *
     * @param userId id del usuario cuyos movimientos se listan
     * @return lista de movimientos, vacía si no tiene ninguno
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listForUser(UUID userId) {
        return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * Recupera un movimiento por id, verificando que pertenece al solicitante.
     *
     * @param id           id del movimiento
     * @param requesterId  id del usuario que hace la petición
     * @return el movimiento si existe y pertenece al solicitante
     * @throws ResponseStatusException 404 si no existe o no le pertenece
     */
    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id, UUID requesterId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));

        if (!transaction.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        }
        return TransactionResponse.from(transaction);
    }

    /**
     * Elimina un movimiento, verificando que pertenece al solicitante.
     *
     * @param id           id del movimiento a borrar
     * @param requesterId  id del usuario que hace la petición
     * @throws ResponseStatusException 404 si no existe o no le pertenece
     */
    @Transactional
    public void delete(UUID id, UUID requesterId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        if (!transaction.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        }
        transactionRepository.delete(transaction);
    }
}