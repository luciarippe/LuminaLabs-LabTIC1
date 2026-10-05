package uy.edu.um.luminalabs.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // "login" puede ser el nombre de usuario o el correo (RF-05)
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        String value = login == null ? "" : login.trim();
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(value, value)
                .map(SecurityUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
