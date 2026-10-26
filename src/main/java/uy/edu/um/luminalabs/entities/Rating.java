package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// RATING del MER (RF-23, CU-08): la valoracion que deja el turista de una reserva terminada.
// Una sola por reserva (restriccion unica de reservation_id) y publica en el detalle de la actividad.
@Entity
@Table(name = "ratings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rating {

    public static final int MAX_STARS = 5;
    public static final int TITLE_MAX_LENGTH = 100;
    public static final int COMMENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    // La actividad de la reserva, guardada aparte para listar y promediar sin pasar por la reserva y el slot
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    // De 1 a MAX_STARS
    @Column(name = "stars", nullable = false)
    private int stars;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "comment", nullable = false, length = COMMENT_MAX_LENGTH)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Moderacion: un administrador la oculto (no se muestra ni cuenta en el promedio). null = visible.
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    // CU-08 1a: solo se valora una reserva terminada (el turista fue a la actividad y ya paso).
    // Que no tenga otra valoracion (4a) lo controla el servicio, y en ultima instancia la base.
    public Rating(Reservation reservation, int stars, String title, String comment, LocalDateTime now) {
        if (reservation.getStatus() != ReservationStatus.FINISHED) {
            throw new IllegalStateException("Vas a poder valorar esta experiencia cuando haya terminado.");
        }
        if (stars < 1 || stars > MAX_STARS) {
            throw new IllegalArgumentException("Stars must be between 1 and " + MAX_STARS);
        }
        if (title == null || title.isBlank() || comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Title and comment are required");
        }
        this.reservation = reservation;
        this.activity = reservation.getSlot().getActivity();
        this.stars = stars;
        this.title = title.strip();
        this.comment = comment.strip();
        this.createdAt = now;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public void hide(String reason, LocalDateTime now) {
        if (isHidden()) {
            throw new IllegalStateException("La valoración ya está oculta.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to hide a rating");
        }
        hiddenAt = now;
        hiddenReason = reason.strip();
    }

    public void restore() {
        if (!isHidden()) {
            throw new IllegalStateException("La valoración no está oculta.");
        }
        hiddenAt = null;
        hiddenReason = null;
    }

    // Como se firma la valoracion en el portal: nombre e inicial del apellido (ej. "Ana P.")
    public String getAuthorName() {
        Tourist tourist = reservation.getTourist();
        String lastName = tourist.getLastName();
        return lastName == null || lastName.isBlank()
                ? tourist.getFirstName()
                : tourist.getFirstName() + " " + lastName.strip().charAt(0) + ".";
    }
}
