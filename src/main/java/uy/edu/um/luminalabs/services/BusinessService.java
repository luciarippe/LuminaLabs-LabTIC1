package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.CancellationReason;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent.Change;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

// Consultas de emprendimientos y cambios de estado hechos por el administrador (CU-06, CU-11)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    // Reservas que todavia ocupan lugares
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final BusinessRepository businessRepository;
    private final ReservationRepository reservationRepository;
    private final CancellationService cancellationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // CU-11 pasos 3 y 3a: cuantas reservas vigentes se van a cancelar si se suspende
    public int countActiveReservations(Long businessId) {
        return activeReservations(businessId).size();
    }

    public BusinessStatusCounts countByStatus() {
        return new BusinessStatusCounts(
                businessRepository.countByStatus(BusinessStatus.PENDING),
                businessRepository.countByStatus(BusinessStatus.APPROVED),
                businessRepository.countByStatus(BusinessStatus.DENIED),
                businessRepository.countByStatus(BusinessStatus.SUSPENDED));
    }

    // status null = todos
    public List<Business> findByStatus(BusinessStatus status) {
        return status == null
                ? businessRepository.findAllByOrderByCreatedAtDesc()
                : businessRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    // Busca por parte del nombre o del RUT/cedula, en todos los estados (el listado cuenta los resultados por estado).
    // El identificador se compara solo con los digitos del texto, asi "21.123" encuentra "211234560019".
    public List<Business> search(String query) {
        String text = query.trim();
        String digits = ProviderRegistrationService.normalizeIdentifier(text);
        return businessRepository.search(escapeLike(text), digits);
    }

    // Secciones "Rechazados" y "Suspendidos" del administrador: el cambio de estado mas reciente primero,
    // opcionalmente filtrados por nombre o RUT/cedula (misma busqueda que el listado general).
    // Un denegado que se vuelve a postular (CU-02 5c) o un suspendido que se reactiva sale de su lista.
    public List<Business> findByStatusLatestFirst(BusinessStatus status, String query) {
        if (query.isBlank()) {
            return businessRepository.findByStatusOrderByReviewedAtDesc(status);
        }
        return search(query).stream()
                .filter(business -> business.getStatus() == status)
                .sorted(Comparator.comparing(Business::getReviewedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // % y _ son comodines en LIKE: se escapan para que se busquen como texto
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public List<Business> findByProvider(Long providerId) {
        return businessRepository.findDistinctByProviders_IdOrderByCreatedAtDesc(providerId);
    }

    public Business findDetail(Long id) {
        return businessRepository.findWithDetailsById(id).orElseThrow(() -> new BusinessNotFoundException(id));
    }

    @Transactional
    public Business approve(Long id) {
        return changeStatus(id, Business::approve, Change.APPROVED);
    }

    @Transactional
    public Business deny(Long id, String reason) {
        return changeStatus(id, (business, now) -> business.deny(reason, now), Change.DENIED);
    }

    // RF-06 y CU-11 paso 8: sus reservas vigentes se cancelan con devolucion total y se avisa a los turistas.
    // Las actividades dejan de mostrarse solas: el catalogo solo muestra emprendimientos aprobados.
    @Transactional
    public Business suspend(Long id, String reason) {
        Business business = changeStatus(id, (found, now) -> found.suspend(reason, now), Change.SUSPENDED);
        cancellationService.cancelWithFullRefund(activeReservations(id), CancellationReason.BUSINESS_SUSPENDED);
        return business;
    }

    @Transactional
    public Business reactivate(Long id) {
        return changeStatus(id, Business::reactivate, Change.REACTIVATED);
    }

    // Reservas pendientes o confirmadas de slots que todavia no empezaron
    private List<Reservation> activeReservations(Long businessId) {
        return reservationRepository.findBySlot_Activity_Business_IdAndStatusInAndSlot_StartTimeAfter(
                businessId, ACTIVE, LocalDateTime.now(clock));
    }

    // Aplica la transicion y avisa a los prestadores por correo (RF-26)
    private Business changeStatus(Long id, BiConsumer<Business, LocalDateTime> transition, Change change) {
        Business business = businessRepository.findById(id).orElseThrow(() -> new BusinessNotFoundException(id));
        transition.accept(business, LocalDateTime.now(clock));
        eventPublisher.publishEvent(BusinessStatusChangedEvent.of(business, change));
        return business;
    }
}
