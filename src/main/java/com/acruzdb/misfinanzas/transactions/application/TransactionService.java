package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
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
 * Además, valida que la categoría indicada (si la hay) sea realmente
 * accesible para el usuario antes de guardar el movimiento.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Da de alta un nuevo movimiento para el usuario indicado.
     *
     * @param user     usuario propietario del movimiento (ya cargado de BD)
     * @param request  datos validados del movimiento a crear
     * @return el movimiento creado, ya con su id asignado
     * @throws ResponseStatusException 400 si se indica un {@code categoryId}
     *         que no existe o no es visible para el usuario (ni suya
     *         propia ni de sistema)
     */
    @Transactional
    public TransactionResponse create(User user, CreateTransactionRequest request) {
        if (request.categoryId() != null) {
            validateCategoryAccess(request.categoryId(), user.getId());
        }

        Transaction transaction = new Transaction(
                user, request.type(), request.amount(), request.transactionDate()
        );
        transaction.setDescription(request.description());
        transaction.setCategoryId(request.categoryId());

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

    @Transactional(readOnly = true)
    public List<TransactionResponse> listForUser(UUID userId) {
        return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id, UUID requesterId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));

        if (!transaction.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        }
        return TransactionResponse.from(transaction);
    }

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