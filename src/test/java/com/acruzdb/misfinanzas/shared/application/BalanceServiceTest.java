package com.acruzdb.misfinanzas.shared.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.dto.BalanceResponse;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.SettlementRepository;
import com.acruzdb.misfinanzas.transactions.domain.ExpenseSplit;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.infrastructure.ExpenseSplitRepository;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link BalanceService}, centrados sobre todo en el
 * algoritmo de simplificación de deudas — es la pieza de lógica más
 * valiosa de todo el módulo del Tricount.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository householdMemberRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private UserRepository userRepository;

    private BalanceService service;
    private UUID householdId;
    private User alex, bea;

    @BeforeEach
    void setUp() throws Exception {
        service = new BalanceService(householdRepository, householdMemberRepository, transactionRepository,
                expenseSplitRepository, settlementRepository, userRepository);
        householdId = UUID.randomUUID();
        alex = new User("alex@test.com", null, "Alex");
        setId(alex, UUID.randomUUID());
        bea = new User("bea@test.com", null, "Bea");
        setId(bea, UUID.randomUUID());
    }

    @Test
    @DisplayName("computeBalances(): con dos personas, quien paga más queda con balance positivo")
    void computeBalances_dosPersonas() {
        Household household = new Household("Casa", alex);
        List<HouseholdMember> members = List.of(
                new HouseholdMember(household, alex, "owner"),
                new HouseholdMember(household, bea, "member")
        );
        when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(members);

        Transaction dinner = new Transaction(alex, "expense", new BigDecimal("40.00"), LocalDate.now());
        when(transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)).thenReturn(List.of(dinner));

        ExpenseSplit alexShare = new ExpenseSplit(dinner, alex, new BigDecimal("20.00"));
        ExpenseSplit beaShare = new ExpenseSplit(dinner, bea, new BigDecimal("20.00"));
        when(expenseSplitRepository.findByHouseholdId(householdId)).thenReturn(List.of(alexShare, beaShare));
        when(settlementRepository.findByHousehold_Id(householdId)).thenReturn(List.of());

        BalanceResponse response = service.computeBalances(householdId, alex.getId());

        // Alex pagó 40, le tocaban 20 -> +20. Bea no pagó nada, le tocaban 20 -> -20.
        var alexBalance = response.balances().stream().filter(b -> b.userId().equals(alex.getId())).findFirst().orElseThrow();
        var beaBalance = response.balances().stream().filter(b -> b.userId().equals(bea.getId())).findFirst().orElseThrow();
        assertThat(alexBalance.netBalance()).isEqualByComparingTo("20.00");
        assertThat(beaBalance.netBalance()).isEqualByComparingTo("-20.00");

        assertThat(response.suggestedSettlements()).hasSize(1);
        var suggestion = response.suggestedSettlements().get(0);
        assertThat(suggestion.fromUserId()).isEqualTo(bea.getId());
        assertThat(suggestion.toUserId()).isEqualTo(alex.getId());
        assertThat(suggestion.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("computeBalances(): con tres personas, la simplificación reduce a menos transferencias que gastos")
    void computeBalances_tresPersonasSimplifica() throws Exception {
        User carla = new User("carla@test.com", null, "Carla");
        setId(carla, UUID.randomUUID());

        Household household = new Household("Piso", alex);
        List<HouseholdMember> members = List.of(
                new HouseholdMember(household, alex, "owner"),
                new HouseholdMember(household, bea, "member"),
                new HouseholdMember(household, carla, "member")
        );
        when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(members);
        when(transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)).thenReturn(List.of());
        when(settlementRepository.findByHousehold_Id(householdId)).thenReturn(List.of());

        // Alex pagó 30 (le tocaban 10 -> +20), Bea no pagó nada (le tocaban 10 -> -10),
        // Carla no pagó nada (le tocaban 10 -> -10). Balance total: +20, -10, -10.
        Transaction expense = new Transaction(alex, "expense", new BigDecimal("30.00"), LocalDate.now());
        when(expenseSplitRepository.findByHouseholdId(householdId)).thenReturn(List.of(
                new ExpenseSplit(expense, alex, new BigDecimal("10.00")),
                new ExpenseSplit(expense, bea, new BigDecimal("10.00")),
                new ExpenseSplit(expense, carla, new BigDecimal("10.00"))
        ));
        when(transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)).thenReturn(List.of(expense));

        BalanceResponse response = service.computeBalances(householdId, alex.getId());

        // Con el algoritmo voraz, esto se resuelve en 2 transferencias
        // (Bea y Carla, cada una, pagan 10 a Alex) -- el mínimo posible
        // para saldar a 3 personas con un solo gasto compartido.
        assertThat(response.suggestedSettlements()).hasSize(2);
        assertThat(response.suggestedSettlements())
                .allMatch(s -> s.toUserId().equals(alex.getId()))
                .allMatch(s -> s.amount().compareTo(new BigDecimal("10.00")) == 0);
    }

    @Test
    @DisplayName("computeBalances(): balances ya saldados no generan sugerencias de liquidación")
    void computeBalances_balancesSaldadosSinSugerencias() {
        Household household = new Household("Casa", alex);
        List<HouseholdMember> members = List.of(
                new HouseholdMember(household, alex, "owner"),
                new HouseholdMember(household, bea, "member")
        );
        when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(members);
        when(transactionRepository.findByHouseholdIdOrderByTransactionDateDesc(householdId)).thenReturn(List.of());
        when(expenseSplitRepository.findByHouseholdId(householdId)).thenReturn(List.of());
        when(settlementRepository.findByHousehold_Id(householdId)).thenReturn(List.of());

        BalanceResponse response = service.computeBalances(householdId, alex.getId());

        assertThat(response.suggestedSettlements()).isEmpty();
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}