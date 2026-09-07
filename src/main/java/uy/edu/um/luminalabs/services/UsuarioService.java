package uy.edu.um.luminalabs.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.luminalabs.entities.Usuario;
import uy.edu.um.luminalabs.repository.UsuarioRepository;


//Los servicios me dan la logica de negocio
//Puedo crear funciones que me permitan trabajar crear destruir usuarios

@Service
public class UsuarioService {

    //Inyectame (instanciame) aca adentro el repositorio de usuarios asi lo puedo usar - IoC (Inversion of Control)
    @Autowired
    private UsuarioRepository usuarioRepository;

    public boolean altaUsuario(String nombre, String apellido){
        if(nombre == null || apellido == null)
            return false;

        int i = 1;
        String userName = nombre+"."+ apellido;

        while (usuarioRepository.existsByUserName(userName)) {
            userName = userName + i++;
        }

        Usuario nuevoUsuario = Usuario.builder()
                .name(nombre)
                .lastName(apellido)
                .userName(userName)
                .build();

        nuevoUsuario = usuarioRepository.save(nuevoUsuario);

        //Condicional en una sola linea
        return nuevoUsuario.getId()>0L? true : false;

    }
}
