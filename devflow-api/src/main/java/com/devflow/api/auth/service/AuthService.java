package com.devflow.api.auth.service;

import com.devflow.api.auth.api.AuthResponse;
import com.devflow.api.auth.api.LoginRequest;
import com.devflow.api.auth.api.RegisterRequest;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.security.JwtService;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$AIXtiDIN/Wo4J./C/PIr.eJn.JGU.q3bItMs9H.dj8Y8FqxBWm83K";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        Instant now = Instant.now();
        User user = new User(email, normalizeDisplayName(request.displayName()), null,
                passwordEncoder.encode(request.password()), true, now, now);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        String passwordHash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean valid = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !user.isActive() || !valid) {
            throw new InvalidCredentialsException();
        }
        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.expiresInSeconds(), user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
