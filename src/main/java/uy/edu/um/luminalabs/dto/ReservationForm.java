package uy.edu.um.luminalabs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.Slot;

import java.util.ArrayList;
import java.util.List;

// CU-01 pasos 2 y 3: el slot elegido y los participantes. En el HTML los participantes se llaman
// "participants[0].firstName", "participants[1].firstName", etc.; Spring arma la lista con esos indices.
@Getter
@Setter
public class ReservationForm {

    // Maximo de personas en una misma reserva (ademas del limite de lugares del slot)
    public static final int MAX_PARTICIPANTS = 20;

    // Cuantas personas se pueden elegir para un slot: sus lugares libres, sin pasar del maximo por reserva
    public static int maxParticipantsFor(Slot slot) {
        return Math.min(slot.getAvailability(), MAX_PARTICIPANTS);
    }

    @NotNull
    private Long slotId;

    // @Valid sobre el tipo de los elementos: valida tambien cada ParticipantForm de la lista
    @Size(min = 1, max = MAX_PARTICIPANTS, message = "Entre 1 y " + MAX_PARTICIPANTS + " participantes por reserva.")
    private List<@Valid ParticipantForm> participants = new ArrayList<>();
}
