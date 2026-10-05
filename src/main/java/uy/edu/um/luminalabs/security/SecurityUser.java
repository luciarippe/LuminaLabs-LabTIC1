package uy.edu.um.luminalabs.security;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.Role;
import uy.edu.um.luminalabs.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Usuario autenticado que se guarda en la sesion. Es una copia liviana de la entidad
// para no guardar objetos de JPA en la sesion HTTP.
@Getter
public class SecurityUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private final String displayName;
    private final Role role;
    private final boolean loginAllowed;
    // Solo para prestadores sin emprendimientos aprobados: el estado a informar al rechazar el login
    private final BusinessStatus blockingStatus;
    private String password;

    private SecurityUser(User user, boolean loginAllowed, BusinessStatus blockingStatus) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.displayName = user.getFirstName();
        this.role = user.getRole();
        this.loginAllowed = loginAllowed;
        this.blockingStatus = blockingStatus;
    }

    public static SecurityUser from(User user) {
        if (user instanceof Provider provider && !provider.hasApprovedBusiness()) {
            return new SecurityUser(user, false, mostRelevantStatus(provider));
        }
        return new SecurityUser(user, true, null);
    }

    // Si tiene alguna solicitud pendiente se informa eso; si no, suspendido; si no, denegado
    private static BusinessStatus mostRelevantStatus(Provider provider) {
        Set<BusinessStatus> statuses = provider.getBusinesses().stream()
                .map(Business::getStatus)
                .collect(Collectors.toSet());
        for (BusinessStatus status : List.of(BusinessStatus.PENDING, BusinessStatus.SUSPENDED, BusinessStatus.DENIED)) {
            if (statuses.contains(status)) {
                return status;
            }
        }
        return null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isProvider() {
        return role == Role.PROVIDER;
    }

    public boolean isTourist() {
        return role == Role.TOURIST;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
