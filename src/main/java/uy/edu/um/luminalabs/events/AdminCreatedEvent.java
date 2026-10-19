package uy.edu.um.luminalabs.events;

// Alta de un administrador desde el panel: se le mandan el usuario y la contrasena inicial
public record AdminCreatedEvent(MailRecipient recipient, String username, String initialPassword) {
}
