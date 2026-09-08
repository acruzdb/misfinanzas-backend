package com.acruzdb.misfinanzas.shared.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.domain.Settlement;
import com.acruzdb.misfinanzas.shared.dto.BalanceResponse;
import com.acruzdb.misfinanzas.shared.dto.BalanceResponse.MemberBalance;
import com.acruzdb.misfinanzas.shared.dto.BalanceResponse.SuggestedSettlement;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.SettlementRepository;
import com.acruzdb.misfinanzas.transactions.domain.ExpenseSplit;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.infrastructure.ExpenseSplitRepository;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Calcula quién debe cuánto a quién dentro de un household, y sugiere
 * las transferencias mínimas necesarias para saldar todas las deudas.
 * <p>
 * El balance neto de cada miembro es: (lo que ha pagado en gastos
 * compartidos) − (lo que le correspondía pagar según el reparto de
 * cada gasto) + (liquidaciones que ha hecho) − (liquidaciones que ha
 * recibido). Un balance positivo significa que le deben dinero; uno
 * negativo, que debe.
 */
@Service
public class BalanceService {

    // Por debajo de este umbral, un balance se considera saldado —
    // evita que errores de redondeo de céntimos generen sugerencias
    // de transferencias de 0,00 € o balances que nunca llegan a cero exacto.
    private static final BigDecimal THRESHOLD = new BigDecimal("0.01");

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;

    public BalanceService(HouseholdRepository householdRepository,
                          HouseholdMemberRepository householdMemberRepository,
                          TransactionRepository transactionRepository,
                          ExpenseSplitRepository expenseSplitRepository,
                          SettlementRepository settlementRepository,
                          UserRepository userRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.transactionRepository = transactionRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
    }

    /**
     * Calcula los balances netos de todos los miembros de un household
     * y las transferencias mínimas sugeridas para saldarlos.
     *
     * @param householdId id del household
     * @param requesterId id del usuario que hace la petición; debe ser miembro
     * @return balances y sugerencias de liquidación
     * @throws ResponseStatusException 404 si no existe o no eres miembro
     */
    @Transactional(readOnly = true)
    public BalanceResponse computeBalances(UUID householdId, UUID requesterId) {
        List<HouseholdMember> members = requireMembership(householdId, requesterId);

        Map<UUID, BigDecimal> net = new LinkedHashMap<>();
        Map<UUID, String> namesById = new LinkedHashMap<>();
        for (HouseholdMember m : members) {
            net.put(m.getUser().getId(), BigDecimal.ZERO);
            namesById.put(m.getUser().getId(), m.getUser().getDisplayName());
        }

        // Quien paga un gasto se apunta el importe completo a su favor...
        for (Transaction tx : transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)) {
            if (!"expense".equals(tx.getType())) continue;
            net.merge(tx.getUser().getId(), tx.getAmount(), BigDecimal::add);
        }

        // ...y cada reparto resta la parte que le tocaba a cada participante.
        for (ExpenseSplit split : expenseSplitRepository.findByHouseholdId(householdId)) {
            net.merge(split.getUser().getId(), split.getShareAmount().negate(), BigDecimal::add);
        }

        // Las liquidaciones ya registradas ajustan el balance: quien pagó
        // mejora su saldo, quien cobró lo empeora (ya no le deben eso).
        for (Settlement s : settlementRepository.findByHousehold_Id(householdId)) {
            net.merge(s.getFromUser().getId(), s.getAmount(), BigDecimal::add);
            net.merge(s.getToUser().getId(), s.getAmount().negate(), BigDecimal::add);
        }

        List<MemberBalance> balances = members.stream()
                .map(m -> new MemberBalance(m.getUser().getId(), m.getUser().getDisplayName(), net.get(m.getUser().getId())))
                .toList();

        List<SuggestedSettlement> suggestions = simplifyDebts(net, namesById);

        return new BalanceResponse(balances, suggestions);
    }

    /**
     * Registra que el usuario autenticado ya le pagó a otro miembro del
     * household una cantidad concreta, para que deje de contar en los
     * balances pendientes.
     *
     * @param householdId id del household
     * @param fromUserId  quien pagó (el usuario autenticado)
     * @param toUserId    quien recibió el pago
     * @param amount      importe liquidado
     * @throws ResponseStatusException 403 si alguno de los dos no pertenece
     *         al household; 400 si intentas liquidar contigo mismo
     */
    @Transactional
    public void recordSettlement(UUID householdId, UUID fromUserId, UUID toUserId, BigDecimal amount) {
        if (fromUserId.equals(toUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes liquidar una deuda contigo mismo");
        }
        List<HouseholdMember> members = requireMembership(householdId, fromUserId);
        boolean toUserIsMember = members.stream().anyMatch(m -> m.getUser().getId().equals(toUserId));
        if (!toUserIsMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario no pertenece a este household");
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado"));
        User fromUser = userRepository.findById(fromUserId).orElseThrow();
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destinatario no encontrado"));

        settlementRepository.save(new Settlement(household, fromUser, toUser, amount, fromUser));
    }

    private List<HouseholdMember> requireMembership(UUID householdId, UUID userId) {
        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);
        boolean isMember = members.stream().anyMatch(m -> m.getUser().getId().equals(userId));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado");
        }
        return members;
    }

    /**
     * Algoritmo voraz de simplificación de deudas: en cada paso, empareja
     * a quien más debe con a quien más le deben, y liquida el mínimo de
     * los dos importes. Repetido hasta el final, produce como mucho
     * {@code n-1} transferencias para saldar a {@code n} personas —
     * muchas menos que si cada gasto generara su propia deuda cruzada.
     *
     * @param netBalances balance neto de cada usuario (positivo = le deben)
     * @param namesById   nombre visible de cada usuario, para la respuesta
     * @return la lista mínima de transferencias sugeridas
     */
    private List<SuggestedSettlement> simplifyDebts(Map<UUID, BigDecimal> netBalances, Map<UUID, String> namesById) {
        List<Bucket> creditors = new ArrayList<>();
        List<Bucket> debtors = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal value = entry.getValue();
            if (value.compareTo(THRESHOLD) > 0) {
                creditors.add(new Bucket(entry.getKey(), value));
            } else if (value.compareTo(THRESHOLD.negate()) < 0) {
                debtors.add(new Bucket(entry.getKey(), value.abs()));
            }
        }
        creditors.sort((a, b) -> b.remaining.compareTo(a.remaining));
        debtors.sort((a, b) -> b.remaining.compareTo(a.remaining));

        List<SuggestedSettlement> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Bucket debtor = debtors.get(i);
            Bucket creditor = creditors.get(j);
            BigDecimal amount = debtor.remaining.min(creditor.remaining);

            result.add(new SuggestedSettlement(
                    debtor.userId, namesById.get(debtor.userId),
                    creditor.userId, namesById.get(creditor.userId),
                    amount
            ));

            debtor.remaining = debtor.remaining.subtract(amount);
            creditor.remaining = creditor.remaining.subtract(amount);
            if (debtor.remaining.compareTo(THRESHOLD) <= 0) i++;
            if (creditor.remaining.compareTo(THRESHOLD) <= 0) j++;
        }
        return result;
    }

    /** Acumulador mutable usado solo dentro de {@link #simplifyDebts}. */
    private static final class Bucket {
        final UUID userId;
        BigDecimal remaining;

        Bucket(UUID userId, BigDecimal remaining) {
            this.userId = userId;
            this.remaining = remaining;
        }
    }
}