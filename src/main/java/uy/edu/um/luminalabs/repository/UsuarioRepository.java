package uy.edu.um.luminalabs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.Usuario;

//Este es el repositorio de un Usuario que su clave es de tipo Long
// Esto es una Interfaz pero Spring por atras implementa toodo (crea las clases por atras)

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    //El existsBy seguido de la propiedad del objeto por el cual voy a buscar. Utilizar notacion CamelCase
    //Spring por detras crea la consulta SQL, etc
    public boolean existsByUserName (String userName);
    public Usuario findByUserName (String userName);
}
