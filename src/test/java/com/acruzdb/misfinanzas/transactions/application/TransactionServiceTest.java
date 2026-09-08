package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.ExpenseSplitRepository;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link TransactionService}.
 * <p>
 * {@link TransactionRepository}, {@link CategoryRepository},
 * {@link HouseholdMemberRepository} y {@link ExpenseSplitRepository} se
 * sustituyen por mocks: no se toca base de datos real, solo se
 * verifica la lógica de negocio del servicio.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    private TransactionService transactionService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService(
                transactionRepository, categoryRepository, householdMemberRepository, expenseSplitRepository
        );
        testUser = new User("alex@test.com", null, "Alex");
        setId(testUser, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() guarda el movimiento cuando no se indica categoría ni household")
    void create_guardaYDevuelveMovimiento() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("45.90"), LocalDate.now(), "Mercadona", null, null, null
        );
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.create(testUser, request);

        assertThat(response.type()).isEqualTo("expense");
        assertThat(response.amount()).isEqualByComparingTo("45.90");
        assertThat(response.description()).isEqualTo("Mercadona");
        assertThat(response.householdId()).isNull();
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(categoryRepository, never()).findById(any());
        verify(householdMemberRepository, never()).findByHouseholdId(any());
    }

    @Test
    @DisplayName("create() acepta una categoría de sistema aunque no sea del usuario")
    void create_aceptaCategoriaDeSistema() throws Exception {
        Category systemCategory = new Category((User) null, "Comida", "expense");
        UUID categoryId = UUID.randomUUID();
        markAsSystem(systemCategory);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(systemCategory));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("20.00"), LocalDate.now(), null, categoryId, null, null
        );

        TransactionResponse response = transactionService.create(testUser, request);

        assertThat(response.categoryId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("create() lanza 400 si la categoría no existe")
    void create_lanza400SiCategoriaNoExiste() {
        UUID categoriaInexistente = UUID.randomUUID();
        when(categoryRepository.findById(categoriaInexistente)).thenReturn(Optional.empty());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("10.00"), LocalDate.now(), null, categoriaInexistente, null, null
        );

        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no existe");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create() lanza 400 si la categoría es de otro usuario")
    void create_lanza400SiCategoriaEsDeOtroUsuario() throws Exception {
        User otroUsuario = new User("otro@test.com", null, "Otro");
        setId(otroUsuario, UUID.randomUUID());
        Category categoriaAjena = new Category(otroUsuario, "Privada", "expense");
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(categoriaAjena));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("10.00"), LocalDate.now(), null, categoryId, null, null
        );

        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no es válida");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create() crea un movimiento compartido y reparte a partes iguales si el usuario es miembro")
    void create_creaMovimientoCompartidoSiEsMiembro() throws Exception {
        UUID householdId = UUID.randomUUID();
        Household household = new Household("Casa", testUser);

        User otroMiembro = new User("bea@test.com", null, "Bea");
        setId(otroMiembro, UUID.randomUUID());

        HouseholdMember myMembership = new HouseholdMember(household, testUser, "owner");
        HouseholdMember otherMembership = new HouseholdMember(household, otroMiembro, "member");
        when(householdMemberRepository.findByHouseholdId(householdId))
                .thenReturn(List.of(myMembership, otherMembership));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("30.00"), LocalDate.now(), "Compra semanal", null, householdId, null
        );

        TransactionResponse response = transactionService.create(testUser, request);

        assertThat(response.householdId()).isEqualTo(householdId);
        // Reparto a partes iguales entre 2 miembros: se generan 2 ExpenseSplit.
        verify(expenseSplitRepository, times(1)).saveAll(argThat(splits -> {
            List<?> list = (List<?>) splits;
            return list.size() == 2;
        }));
    }

    @Test
    @DisplayName("create() lanza 403 si el usuario no pertenece al household indicado")
    void create_lanza403SiNoPerteneceAlHousehold() {
        UUID householdId = UUID.randomUUID();
        // Lista de miembros vacía -> testUser no está entre ellos.
        when(householdMemberRepository.findByHouseholdId(householdId)).thenReturn(List.of());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("30.00"), LocalDate.now(), null, null, householdId, null
        );

        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No perteneces");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento pertenece a otro usuario")
    void getById_lanza404SiNoEsElPropietario() {
        Transaction transaction = new Transaction(testUser, "expense", new BigDecimal("10.00"), LocalDate.now());
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        UUID otroUsuarioId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionService.getById(transactionId, otroUsuarioId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Movimiento no encontrado");
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento no existe en absoluto")
    void getById_lanza404SiNoExiste() {
        UUID idInexistente = UUID.randomUUID();
        when(transactionRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(idInexistente, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void markAsSystem(Category category) throws Exception {
        Field field = Category.class.getDeclaredField("isSystem");
        field.setAccessible(true);
        field.set(category, true);
    }
}