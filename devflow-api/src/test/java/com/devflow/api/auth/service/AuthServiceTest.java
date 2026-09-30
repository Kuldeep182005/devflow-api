package com.devflow.api.auth.service;

import com.devflow.api.auth.api.AuthResponse;
import com.devflow.api.auth.api.LoginRequest;
import com.devflow.api.auth.api.RegisterRequest;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.security.JwtService;
import com.devflow.api.user.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registersWithNormalizedEmailAndAdaptivePasswordHash() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("token");
        when(jwtService.expiresInSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(
                new RegisterRequest(" Alice@Example.com ", "correct horse", " Alice "));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("correct horse", saved.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void rejectsDuplicateRegistration() {
        Instant now = Instant.now();
        User existing = new User("alice@example.com", "Alice", null, "hash", true, now, now);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "correct horse", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void authenticatesWithStoredPasswordAndRejectsWrongPassword() {
        Instant now = Instant.now();
        String hash = passwordEncoder.encode("correct horse");
        User user = new User("alice@example.com", "Alice", null, hash, true, now, now);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token");
        when(jwtService.expiresInSeconds()).thenReturn(900L);

        assertThat(authService.login(new LoginRequest("ALICE@example.com", "correct horse")).accessToken())
                .isEqualTo("token");
        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
