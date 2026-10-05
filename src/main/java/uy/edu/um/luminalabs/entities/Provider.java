package uy.edu.um.luminalabs.entities;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("PROVIDER")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Provider extends User {

    @ManyToMany(mappedBy = "providers")
    @Builder.Default
    private Set<Business> businesses = new HashSet<>();

    @Override
    public Role getRole() {
        return Role.PROVIDER;
    }

    // RF-05: un prestador solo puede iniciar sesion si tiene al menos un emprendimiento aprobado
    public boolean hasApprovedBusiness() {
        return businesses.stream().anyMatch(business -> business.getStatus() == BusinessStatus.APPROVED);
    }
}
