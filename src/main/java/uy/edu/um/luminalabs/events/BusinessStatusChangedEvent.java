package uy.edu.um.luminalabs.events;

import uy.edu.um.luminalabs.entities.Business;

import java.util.List;

// RF-26: aprobacion, rechazo, suspension y reactivacion, con su motivo cuando corresponde.
// Se avisa a todos los prestadores del emprendimiento.
public record BusinessStatusChangedEvent(List<MailRecipient> recipients, String businessName,
                                         Change change, String reason) {

    // Se guarda el cambio y no solo el estado nuevo: aprobar y reactivar terminan ambos en APPROVED
    public enum Change { APPROVED, DENIED, SUSPENDED, REACTIVATED }

    public static BusinessStatusChangedEvent of(Business business, Change change) {
        List<MailRecipient> recipients = business.getProviders().stream().map(MailRecipient::from).toList();
        return new BusinessStatusChangedEvent(recipients, business.getLegalName(), change, business.getStatusReason());
    }
}
