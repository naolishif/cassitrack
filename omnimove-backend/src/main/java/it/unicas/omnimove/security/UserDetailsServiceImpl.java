package it.unicas.omnimove.security;
import it.unicas.omnimove.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepo;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email)
            .map(u -> User.withUsername(u.getEmail())
                .password(u.getPassword())
                .roles(u.getRole())
                .build())
            .orElseThrow(()->new UsernameNotFoundException("User not found: "+email));
    }
}
