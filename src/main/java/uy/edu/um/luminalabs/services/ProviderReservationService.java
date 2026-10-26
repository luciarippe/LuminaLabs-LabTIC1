package uy.edu.um.luminalabs.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.Cancellation;
import uy.edu.um.luminalabs.entities.Participant;
import uy.edu.um.luminalabs.entities.Payment;
import uy.edu.um.luminalabs.entities.PaymentStatus;
import uy.edu.um.luminalabs.entities.PaymentType;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.exceptions.SlotNotFoundException;
import uy.edu.um.luminalabs.repositories.CancellationRepository;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// CU-09 (RF-22): el prestador consulta las reservas de las actividades de sus emprendimientos, agrupadas por
// horario (slot), con filtros; el detalle de cada una con sus participantes y el pago; y la lista consolidada de
// participantes confirmados de un horario (6a). Una reserva u horario de otro prestador se trata como inexistente.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderReservationService {

    // Sin fecha "desde", se muestran los horarios desde ahora; con estados que suelen ser del pasado
    // (terminadas, canceladas...), desde esta fecha
    private static final LocalDateTime BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final ReservationRepository reservationRepository;
    private final SlotRepository slotRepository;
    private final PaymentRepository paymentRepository;
    private final CancellationRepository cancellationRepository;
    private final Clock clock;

    // Paso 3: filtro por estado. upcomingByDefault: sin fechas, solo horarios que no empezaron (las vigentes
    // interesan para lo que viene; las terminadas o canceladas, para mirar hacia atras)
    @Getter
    public enum StatusFilter {
        ACTIVE("Vigentes", true, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED),
        CONFIRMED("Confirmadas", true, ReservationStatus.CONFIRMED),
        PENDING_PAYMENT("Pendientes de pago", true, ReservationStatus.PENDING_PAYMENT),
        FINISHED("Terminadas", false, ReservationStatus.FINISHED),
        CANCELLED("Canceladas", false, ReservationStatus.CANCELLED),
        EXPIRED("Vencidas", false, ReservationStatus.EXPIRED),
        ALL("Todas", false, ReservationStatus.values());

        private final String label;
        private final boolean upcomingByDefault;
        private final List<ReservationStatus> statuses;

        StatusFilter(String label, boolean upcomingByDefault, ReservationStatus... statuses) {
            this.label = label;
            this.upcomingByDefault = upcomingByDefault;
            this.statuses = List.of(statuses);
        }
    }

    // Paso 3: los filtros (null = sin filtrar). Sin nada elegido muestra las vigentes de los proximos horarios.
    public record Filters(Long businessId, Long activityId, LocalDate dateFrom, LocalDate dateTo, StatusFilter status) {

        public Filters {
            status = status == null ? StatusFilter.ACTIVE : status;
            // Si las fechas vienen al reves, se dan vuelta
            if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
                LocalDate swap = dateFrom;
                dateFrom = dateTo;
                dateTo = swap;
            }
        }

        public boolean isDefault() {
            return businessId == null && activityId == null && dateFrom == null && dateTo == null
                    && status == StatusFilter.ACTIVE;
        }
    }

    // Pasos 2 a 4: las reservas que cumplen los filtros, agrupadas por horario y en orden de fecha
    public List<SlotReservations> find(Long providerId, Filters filters) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = filters.dateFrom() != null ? filters.dateFrom().atStartOfDay()
                : (filters.status().isUpcomingByDefault() ? now : BEGINNING);
        LocalDateTime to = filters.dateTo() != null ? filters.dateTo().plusDays(1).atStartOfDay() : END;
        Map<Slot, List<Reservation>> bySlot = reservationRepository.findForProvider(providerId, filters.businessId(),
                        filters.activityId(), from, to, filters.status().getStatuses())
                .stream()
                // LinkedHashMap: mantiene el orden por fecha que trae la consulta
                .collect(Collectors.groupingBy(Reservation::getSlot, LinkedHashMap::new, Collectors.toList()));
        LocalDate today = now.toLocalDate();
        return bySlot.entrySet().stream()
                .map(entry -> new SlotReservations(entry.getKey(), entry.getValue(),
                        ChronoUnit.DAYS.between(today, entry.getKey().getStartTime().toLocalDate())))
                .toList();
    }

    // 2a: todavia no recibio ninguna reserva (si no, una lista vacia es porque no coincide con los filtros, 4a)
    public boolean hasAnyReservation(Long providerId) {
        return reservationRepository.existsForProvider(providerId);
    }

    // Pasos 5 y 6: el detalle de una reserva, con el turista, los participantes y el estado del pago
    public ReservationDetail findReservation(Long providerId, Long reservationId) {
        Reservation reservation = reservationRepository.findWithDetailsById(reservationId)
                .filter(found -> found.getSlot().getActivity().isManagedBy(providerId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        List<Payment> payments = paymentRepository.findByReservation_IdOrderByCreatedAtAsc(reservationId);
        Cancellation cancellation = cancellationRepository.findByReservation_Id(reservationId).orElse(null);
        return new ReservationDetail(reservation, payments, PaymentSummary.of(reservation, payments), cancellation);
    }

    // 6a: todos los participantes confirmados del horario, para organizar la salida, y cuantos lugares
    // estan todavia pendientes de pago
    public SlotParticipants findSlotParticipants(Long providerId, Long slotId) {
        Slot slot = slotRepository.findWithActivityById(slotId)
                .filter(found -> found.getActivity().isManagedBy(providerId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));
        List<Reservation> reservations = reservationRepository.findWithParticipantsBySlot_IdAndStatusInOrderByCreatedAtAsc(
                slotId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.FINISHED, ReservationStatus.PENDING_PAYMENT));
        Map<Boolean, List<Reservation>> byPending = reservations.stream()
                .collect(Collectors.partitioningBy(Reservation::isPendingPayment));
        return new SlotParticipants(slot, byPending.get(false),
                byPending.get(true).stream().mapToInt(Reservation::getParticipantCount).sum());
    }

    // Un horario con sus reservas (las que cumplen los filtros). daysLeft: dias de calendario hasta el horario
    // (0 = hoy, negativo = ya paso).
    public record SlotReservations(Slot slot, List<Reservation> reservations, long daysLeft) {

        public int confirmedSeats() {
            return seats(ReservationStatus.CONFIRMED);
        }

        public int pendingSeats() {
            return seats(ReservationStatus.PENDING_PAYMENT);
        }

        // Porcentaje de la capacidad ocupado, para la barra de la tarjeta
        public int occupancyPercent() {
            return slot.getReservedSeats() * 100 / slot.getMaxCapacity();
        }

        public boolean isPast() {
            return daysLeft < 0;
        }

        private int seats(ReservationStatus status) {
            return reservations.stream()
                    .filter(reservation -> reservation.getStatus() == status)
                    .mapToInt(Reservation::getParticipantCount)
                    .sum();
        }
    }

    public record ReservationDetail(Reservation reservation, List<Payment> payments, PaymentSummary paymentSummary,
                                    Cancellation cancellation) {
    }

    // Paso 6: el estado del pago en una frase, y si hubo devolucion. tone: clase del badge (approved, pending, denied...)
    public record PaymentSummary(String label, String tone, Payment charge, Payment refund) {

        static PaymentSummary of(Reservation reservation, List<Payment> payments) {
            Payment charge = payments.stream().filter(Payment::isApprovedCharge).findFirst().orElse(null);
            Payment refund = payments.stream().filter(payment -> payment.getType() == PaymentType.REFUND)
                    .reduce((first, second) -> second).orElse(null);
            if (charge != null) {
                return new PaymentSummary(refund == null ? "Pagada" : "Pagada y devuelta", "approved", charge, refund);
            }
            boolean awaiting = payments.stream().anyMatch(payment -> payment.getType() == PaymentType.CHARGE
                    && payment.getStatus() == PaymentStatus.NO_RESPONSE);
            if (awaiting) {
                return new PaymentSummary("Pago sin confirmar por la pasarela", "pending", null, null);
            }
            return switch (reservation.getStatus()) {
                case PENDING_PAYMENT -> new PaymentSummary("Todavía no pagó", "pending", null, null);
                case EXPIRED -> new PaymentSummary("Venció sin pago", "suspended", null, null);
                default -> new PaymentSummary("Sin pago", "suspended", null, null);
            };
        }
    }

    // 6a. confirmed: las reservas pagadas (confirmadas o ya terminadas), con sus participantes
    public record SlotParticipants(Slot slot, List<Reservation> confirmed, int pendingSeats) {

        public int confirmedSeats() {
            return confirmed.stream().mapToInt(Reservation::getParticipantCount).sum();
        }

        // Una fila por participante, numeradas de corrido y agrupadas por reserva (en el orden en que se hicieron)
        public List<ParticipantRow> rows() {
            List<ParticipantRow> rows = new ArrayList<>();
            for (Reservation reservation : confirmed) {
                boolean first = true;
                for (Participant participant : reservation.getParticipants()) {
                    rows.add(new ParticipantRow(rows.size() + 1, participant, reservation, first));
                    first = false;
                }
            }
            return rows;
        }
    }

    // firstOfReservation: la primera fila de su reserva (ahi se muestra quien reservo)
    public record ParticipantRow(int number, Participant participant, Reservation reservation, boolean firstOfReservation) {
    }
}
