package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
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
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    // Inyección por constructor, no por @Autowired en el campo:
    // hace explícitas las dependencias y facilita los tests más adelante
    // (se instancia el servicio pasando mocks directamente, sin contexto Spring).
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Da de alta un nuevo movimiento para el usuario indicado.
     *
     * @param user     usuario propietario del movimiento (ya cargado de BD)
     * @param request  datos validados del movimiento a crear
     * @return el movimiento creado, ya con su id asignado
     */
    @Transactional
    public TransactionResponse create(User user, CreateTransactionRequest request) {
        Transaction transaction = new Transaction(
                user, request.type(), request.amount(), request.transactionDate()
        );
        transaction.setDescription(request.description());
        transaction.setCategoryId(request.categoryId());

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    /**
     * Lista todos los movimientos del usuario, del más reciente al más antiguo.
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
     *         (no distinguimos los dos casos por seguridad, ver nota en el chat)
     */
    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id, UUID requesterId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));

        // Comprobación de autorización básica: si no eres el dueño, ni existe para ti.
        // Esto es justo lo que comentábamos en el apartado de seguridad: nunca fiarse
        // del id que llega por la URL sin verificar que pertenece a quien pregunta.
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