package ec.edu.espe.backend.service.impl;

import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.dto.AuthRequestDTO;
import ec.edu.espe.backend.dto.AuthResponseDTO;
import ec.edu.espe.backend.dto.RegisterRequestDTO;
import ec.edu.espe.backend.exception.DuplicateEmailException;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.security.JwtService;
import ec.edu.espe.backend.security.UserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Servicio de autenticación reactivo.
 * En WebFlux no se usa AuthenticationManager (bloqueante);
 * la validación de credenciales se hace manualmente de forma reactiva.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    // Registro reactivo
    public Mono<AuthResponseDTO> register(RegisterRequestDTO request) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateEmailException("Email ya registrado"));
                    }
                    User user = new User();
                    user.setName(request.getName());
                    user.setEmail(request.getEmail());
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
                    user.setRole(User.Role.USER);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .map(saved -> {
                    String token = jwtService.generateToken(new UserPrincipal(saved));
                    return new AuthResponseDTO(token);
                });
    }

    // Login reactivo: valida credenciales sin AuthenticationManager bloqueante
    public Mono<AuthResponseDTO> login(AuthRequestDTO request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new BadCredentialsException("Correo o contraseña incorrectos.")))
                .flatMap(user -> {
                    if (!user.getActive()) {
                        return Mono.error(new BadCredentialsException("La cuenta está desactivada."));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new BadCredentialsException("Correo o contraseña incorrectos."));
                    }
                    String token = jwtService.generateToken(new UserPrincipal(user));
                    return Mono.just(new AuthResponseDTO(token));
                });
    }
}