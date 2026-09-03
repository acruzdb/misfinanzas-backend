package com.acruzdb.misfinanzas.shared.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.dto.HouseholdResponse;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio del módulo de grupos compartidos (households).
 * <p>
 * <b>Alcance actual:</b> añadir un miembro requiere que ya tenga una
 * cuenta creada (login previo por su cuenta); no existe todavía el
 * flujo de invitación por email a alguien sin cuenta (tabla
 * {@code household_invites}, pendiente de un paso futuro que incluya
 * envío de emails).
 */
@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserRepository userRepository;

    public HouseholdService(HouseholdRepository householdRepository,
                            HouseholdMemberRepository householdMemberRepository,
                            UserRepository userRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Crea un nuevo household y da de alta a su creador como owner.
     *
     * @param creator usuario que crea el grupo
     * @param name    nombre visible del grupo
     * @return el household creado, con su creador como único miembro
     */
    @Transactional
    public HouseholdResponse create(User creator, String name) {
        Household household = householdRepository.save(new Household(name, creator));
        HouseholdMember ownerMembership = householdMemberRepository.save(
                new HouseholdMember(household, creator, "owner")
        );
        return HouseholdResponse.from(household, List.of(ownerMembership));
    }

    /**
     * Lista los households a los que pertenece un usuario.
     *
     * @param userId id del usuario
     * @return un resumen por cada household del que es miembro
     */
    @Transactional(readOnly = true)
    public List<HouseholdResponse> listForUser(UUID userId) {
        return householdMemberRepository.findByUserId(userId).stream()
                .map(membership -> {
                    Household household = membership.getHousehold();
                    List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(household.getId());
                    return HouseholdResponse.from(household, members);
                })
                .toList();
    }

    /**
     * Obtiene el detalle de un household, incluidos sus miembros.
     *
     * @param householdId id del household
     * @param requesterId id del usuario que hace la petición
     * @return el household con su lista de miembros
     * @throws ResponseStatusException 404 si no existe o el solicitante
     *         no es miembro (no distinguimos los dos casos, por seguridad)
     */
    @Transactional(readOnly = true)
    public HouseholdResponse getById(UUID householdId, UUID requesterId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado"));

        requireMembership(householdId, requesterId);

        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);
        return HouseholdResponse.from(household, members);
    }

    /**
     * Añade un usuario existente (por email) a un household.
     *
     * @param householdId id del household
     * @param requesterId id de quien hace la petición; debe ser owner
     * @param email       email del usuario a añadir
     * @return el household actualizado con el nuevo miembro
     * @throws ResponseStatusException 404 si el household no existe, o si
     *         no hay ningún usuario registrado con ese email; 403 si el
     *         solicitante no es owner del household; 409 si el usuario
     *         ya es miembro
     */
    @Transactional
    public HouseholdResponse addMember(UUID householdId, UUID requesterId, String email) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado"));

        HouseholdMember requesterMembership = requireMembership(householdId, requesterId);
        if (!requesterMembership.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el propietario puede añadir miembros");
        }

        User newMember = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe ningún usuario registrado con ese email"));

        if (householdMemberRepository.findByHouseholdIdAndUserId(householdId, newMember.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese usuario ya es miembro del household");
        }

        householdMemberRepository.save(new HouseholdMember(household, newMember, "member"));
        List<HouseholdMember> members = householdMemberRepository.findByHouseholdId(householdId);
        return HouseholdResponse.from(household, members);
    }

    /**
     * El usuario abandona un household del que es miembro.
     * <p>
     * Simplificación deliberada: no hay lógica de transferencia de
     * propiedad — si el owner se va, el household se queda sin owner.
     * Aceptable mientras el caso de uso principal sea "pareja", donde
     * es poco probable que uno se vaya dejando al otro fuera; se
     * revisará si el modelo crece a grupos más grandes.
     *
     * @param householdId id del household a abandonar
     * @param userId      id del usuario que abandona
     */
    @Transactional
    public void leave(UUID householdId, UUID userId) {
        HouseholdMember membership = requireMembership(householdId, userId);
        householdMemberRepository.delete(membership);
    }

    private HouseholdMember requireMembership(UUID householdId, UUID userId) {
        return householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado"));
    }
}