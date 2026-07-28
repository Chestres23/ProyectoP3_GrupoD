package ec.edu.espe.backend.service;

import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.dto.AuthRequestDTO;
import ec.edu.espe.backend.dto.RegisterRequestDTO;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.security.JwtService;
import ec.edu.espe.backend.security.UserPrincipal;
import ec.edu.espe.backend.service.impl.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtService, passwordEncoder);
    }

    @Test
    void register_shouldPersistUser_andReturnToken() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setName("Juan Perez");
        request.setEmail("juan@espe.edu.ec");
        request.setPassword("secret123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(Mono.just(false));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("ENCODED-PASS");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return Mono.just(user);
        });
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-register-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        StepVerifier.create(authService.register(request))
                .assertNext(response -> assertThat(response.getToken()).isEqualTo("jwt-register-token"))
                .verifyComplete();

        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getName()).isEqualTo("Juan Perez");
        assertThat(savedUser.getEmail()).isEqualTo("juan@espe.edu.ec");
        assertThat(savedUser.getPassword()).isEqualTo("ENCODED-PASS");
        assertThat(savedUser.getRoleValue()).isEqualTo("USER");
        assertThat(savedUser.getActive()).isTrue();
    }

    @Test
    void register_duplicateEmail_shouldFail_andNotSave() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setName("Juan Perez");
        request.setEmail("juan@espe.edu.ec");
        request.setPassword("secret123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(Mono.just(true));

        StepVerifier.create(authService.register(request))
                .expectErrorMessage("Email ya registrado")
                .verify();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void login_shouldReturnTokenForValidCredentials() {
        AuthRequestDTO request = new AuthRequestDTO();
        request.setEmail("juan@espe.edu.ec");
        request.setPassword("secret123");

        User user = new User();
        user.setId(5L);
        user.setName("Juan Perez");
        user.setEmail(request.getEmail());
        user.setPassword("ENCODED-PASS");
        user.setActive(true);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Mono.just(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-login-token");

        StepVerifier.create(authService.login(request))
                .assertNext(response -> assertThat(response.getToken()).isEqualTo("jwt-login-token"))
                .verifyComplete();

        verify(passwordEncoder).matches(request.getPassword(), user.getPassword());
        verify(jwtService).generateToken(any(UserPrincipal.class));
    }

    @Test
    void login_wrongPassword_shouldFail_andNotGenerateToken() {
        AuthRequestDTO request = new AuthRequestDTO();
        request.setEmail("juan@espe.edu.ec");
        request.setPassword("wrong-pass");

        User user = new User();
        user.setId(5L);
        user.setName("Juan Perez");
        user.setEmail(request.getEmail());
        user.setPassword("ENCODED-PASS");
        user.setActive(true);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Mono.just(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(false);

        StepVerifier.create(authService.login(request))
                .expectError(BadCredentialsException.class)
                .verify();

        verify(jwtService, never()).generateToken(any());
    }
}