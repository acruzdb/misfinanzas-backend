package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.transactions.domain.ExpenseSplit;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.SplitInput;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.ExpenseSplitRepository;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ExpenseSplitRepository expenseSplitRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              HouseholdMemberRepository householdMemberRepository,
                              ExpenseSplitRepository expenseSplitRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.expenseSplitRepository = expenseSplitRepository;
    }

    /**
     * Da de alta un nuevo movimiento para el usuario indicado.
     * <p>
     * Si es un gasto de household, genera además su reparto: el indicado
     * explícitamente en {@code request.splits()}, o a partes iguales entre
     * todos los miembros si no se especifica ninguno.
     *
     * @param user     usuario propietario del movimiento
     * @param request  datos validados del movimiento a crear
     * @return el movimiento creado
     * @throws ResponseStatusException 400 si la categoría o el reparto no son
     *         válidos; 403 si el household indicado no es tuyo
     */
    @Transactional
    public TransactionResponse create(User user, CreateTransactionRequest request) {
        if (request.categoryId() != null) {
            validateCategoryAccess(request.categoryId(), user.getId());
        }

        List<User> householdMembers = null;
        if (request.householdId() != null) {
            householdMembers = requireMembershipAndListMembers(request.householdId(), user.getId());
        }

        Transaction transaction = new Transaction(user, request.type(), request.amount(), request.transactionDate());
        transaction.setDescription(request.description());
        transaction.setCategoryId(request.categoryId());
        transaction.setHouseholdId(request.householdId());

        Transaction saved = transactionRepository.save(transaction);

        if (householdMembers != null && "expense".equals(request.type())) {
            List<ExpenseSplit> splits = buildSplits(saved, request.splits(), householdMembers, request.amount());
            expenseSplitRepository.saveAll(splits);
        }

        return TransactionResponse.from(saved);
    }

    /**
     * Comprueba que el usuario pertenece al household y devuelve la lista
     * de todos sus miembros — se necesita de todas formas para el reparto
     * por defecto, así que evitamos una segunda consulta después.
     */
    private List<User> requireMembershipAndListMembers(UUID householdId, UUID userId) {
        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);
        boolean isMember = members.stream().anyMatch(m -> Objects.equals(m.getUser().getId(), userId));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a ese household");
        }
        return members.stream().map(HouseholdMember::getUser).toList();
    }

    private List<ExpenseSplit> buildSplits(Transaction transaction, List<SplitInput> requested,
                                           List<User> householdMembers, BigDecimal totalAmount) {
        if (requested != null && !requested.isEmpty()) {
            return buildCustomSplits(transaction, requested, householdMembers, totalAmount);
        }
        return buildEqualSplits(transaction, householdMembers, totalAmount);
    }

    /**
     * Construye el reparto a partir de las partes indicadas explícitamente
     * por el usuario, validando que cada participante pertenece al
     * household y que la suma coincide exactamente con el importe total.
     */
    private List<ExpenseSplit> buildCustomSplits(Transaction transaction, List<SplitInput> requested,
                                                 List<User> householdMembers, BigDecimal totalAmount) {
        Map<UUID, User> membersById = householdMembers.stream().collect(Collectors.toMap(User::getId, u -> u));

        BigDecimal sum = BigDecimal.ZERO;
        List<ExpenseSplit> splits = new ArrayList<>();
        for (SplitInput input : requested) {
            User participant = membersById.get(input.userId());
            if (participant == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Uno de los participantes del reparto no pertenece a este household");
            }
            sum = sum.add(input.shareAmount());
            splits.add(new ExpenseSplit(transaction, participant, input.shareAmount()));
        }
        if (sum.compareTo(totalAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La suma del reparto (" + sum + ") no coincide con el importe del movimiento (" + totalAmount + ")");
        }
        return splits;
    }

    private List<ExpenseSplit> buildEqualSplits(Transaction transaction, List<User> householdMembers, BigDecimal totalAmount) {
        List<BigDecimal> shares = divideEqually(totalAmount, householdMembers.size());
        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < householdMembers.size(); i++) {
            splits.add(new ExpenseSplit(transaction, householdMembers.get(i), shares.get(i)));
        }
        return splits;
    }

    /**
     * Reparte un importe entre {@code n} personas a partes iguales,
     * garantizando que la suma de las partes coincide EXACTAMENTE con el
     * importe original.
     * <p>
     * Una división simple ({@code amount / n}) puede perder o sobrar
     * céntimos por redondeo (10.00 € entre 3 personas no da un número
     * exacto de céntimos). El resto se reparte de uno en uno entre las
     * primeras personas de la lista hasta que la suma cuadra del todo.
     *
     * @param amount importe total a repartir
     * @param n      número de participantes
     * @return una parte por participante, en el mismo orden recibido
     */
    private List<BigDecimal> divideEqually(BigDecimal amount, int n) {
        BigDecimal base = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal distributed = base.multiply(BigDecimal.valueOf(n));
        BigDecimal remainderCents = amount.subtract(distributed).divide(new BigDecimal("0.01"));
        int extraCents = remainderCents.intValue();

        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            shares.add(i < extraCents ? base.add(new BigDecimal("0.01")) : base);
        }
        return shares;
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

    /**
     * Lista los movimientos de un household, de cualquiera de sus
     * miembros, no solo los del solicitante — es la diferencia clave
     * frente a listForUser(), pensada para la vista de gastos compartidos.
     *
     * @param householdId id del household
     * @param requesterId id del usuario que hace la petición; debe ser miembro
     * @return movimientos del grupo, del más reciente al más antiguo
     * @throws ResponseStatusException 403 si el solicitante no pertenece al household
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listForHousehold(UUID householdId, UUID requesterId) {
        validateHouseholdMembership(householdId, requesterId);
        return transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }
}