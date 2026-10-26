package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // RF-05: se inicia sesion con correo o nombre de usuario
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select count(a) from Admin a")
    long countAdmins();

    // Gestion de usuarios: todas las cuentas, la mas nueva primero
    List<User> findAllByOrderByIdDesc();

    // No se puede bloquear al ultimo administrador activo
    @Query("select count(a) from Admin a where a.blockedAt is null")
    long countActiveAdmins();
}
