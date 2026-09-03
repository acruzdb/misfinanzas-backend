package com.acruzdb.misfinanzas.shared.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.JwtService;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.dto.HouseholdResponse;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link HouseholdService}, con los tres repositorios mockeados.
 */
@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository householdMemberRepository;
    @Mock private UserRepository userRepository;
    @MockitoBean private JwtService jwtService;

    private HouseholdService householdService;
    private User owner;
    private User invitee;

    @BeforeEach
    void setUp() throws Exception {
        householdService = new HouseholdService(householdRepository, householdMemberRepository, userRepository);
        owner = new User("owner@test.com", null, "Owner");
        setId(owner, UUID.randomUUID());
        invitee = new User("invitee@test.com", null, "Invitee");
        setId(invitee, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() guarda el household y da de alta al creador como owner")
    void create_creaHouseholdConOwner() {
        when(householdRepository.save(any(Household.class))).thenAnswer(inv -> inv.getArgument(0));
        when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(inv -> inv.getArgument(0));

        HouseholdResponse response = householdService.create(owner, "Casa Alex & Sam");

        assertThat(response.name()).isEqualTo("Casa Alex & Sam");
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).role()).isEqualTo("owner");
    }

    @Test
    @DisplayName("getById() lanza 404 si el solicitante no es miembro")
    void getById_lanza404SiNoEsMiembro() {
        Household household = new Household("Casa", owner);
        UUID householdId = UUID.randomUUID();
        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitee.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> householdService.getById(householdId, invitee.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("addMember() lanza 403 si quien lo pide no es owner")
    void addMember_lanza403SiNoEsOwner() {
        Household household = new Household("Casa", owner);
        UUID householdId = UUID.randomUUID();
        HouseholdMember memberMembership = new HouseholdMember(household, invitee, "member");

        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitee.getId()))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> householdService.addMember(householdId, invitee.getId(), "otro@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("propietario");
    }

    @Test
    @DisplayName("addMember() lanza 404 si el email no corresponde a ningún usuario registrado")
    void addMember_lanza404SiEmailNoExiste() {
        Household household = new Household("Casa", owner);
        UUID householdId = UUID.randomUUID();
        HouseholdMember ownerMembership = new HouseholdMember(household, owner, "owner");

        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, owner.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmailIgnoreCase("noexiste@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> householdService.addMember(householdId, owner.getId(), "noexiste@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No existe");
    }

    @Test
    @DisplayName("addMember() lanza 409 si el usuario ya es miembro")
    void addMember_lanza409SiYaEsMiembro() {
        Household household = new Household("Casa", owner);
        UUID householdId = UUID.randomUUID();
        HouseholdMember ownerMembership = new HouseholdMember(household, owner, "owner");
        HouseholdMember existingMembership = new HouseholdMember(household, invitee, "member");

        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, owner.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmailIgnoreCase("invitee@test.com")).thenReturn(Optional.of(invitee));
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitee.getId()))
                .thenReturn(Optional.of(existingMembership));

        assertThatThrownBy(() -> householdService.addMember(householdId, owner.getId(), "invitee@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya es miembro");
    }

    @Test
    @DisplayName("leave() elimina la pertenencia del usuario")
    void leave_eliminaPertenencia() {
        Household household = new Household("Casa", owner);
        UUID householdId = UUID.randomUUID();
        HouseholdMember membership = new HouseholdMember(household, invitee, "member");

        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, invitee.getId()))
                .thenReturn(Optional.of(membership));

        householdService.leave(householdId, invitee.getId());

        verify(householdMemberRepository, times(1)).delete(membership);
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}