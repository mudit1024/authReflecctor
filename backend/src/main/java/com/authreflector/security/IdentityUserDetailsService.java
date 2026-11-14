package com.authreflector.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.authreflector.identity.UserRepository;

// Tells Spring Security how to find an account by the email typed into the login form
@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public IdentityUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        // Spring Security turns this exception into the same "bad credentials" result as a wrong password,
        // and still runs a dummy password check so both cases take about the same time
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account for the given email"));
    }
}
