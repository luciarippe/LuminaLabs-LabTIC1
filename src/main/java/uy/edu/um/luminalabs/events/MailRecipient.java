package uy.edu.um.luminalabs.events;

import uy.edu.um.luminalabs.entities.User;

// Datos minimos para escribirle a un usuario (los eventos no llevan entidades de JPA)
public record MailRecipient(String email, String firstName) {

    public static MailRecipient from(User user) {
        return new MailRecipient(user.getEmail(), user.getFirstName());
    }
}
