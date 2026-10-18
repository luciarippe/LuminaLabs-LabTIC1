package uy.edu.um.luminalabs.exceptions;

import lombok.Getter;

// CU-01 4a y 5a: no se puede reservar ese slot (sin lugares suficientes, fuera de plazo, cancelado...).
// El mensaje es para el turista; activityId sirve para volver a la actividad y ofrecerle otros slots.
@Getter
public class ReservationNotPossibleException extends RuntimeException {

    private final Long activityId;

    public ReservationNotPossibleException(String message, Long activityId) {
        super(message);
        this.activityId = activityId;
    }
}
