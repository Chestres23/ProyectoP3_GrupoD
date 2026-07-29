package ec.edu.espe.backend.service.impl;

import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.dto.AuthRequestDTO;
import ec.edu.espe.backend.dto.AuthResponseDTO;
import ec.edu.espe.backend.dto.RegisterRequestDTO;
import ec.edu.espe.backend.exception.DuplicateEmailException;
import ec.edu.espe.backend.reactive.service.ReactiveClaimService;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.security.JwtService;
import ec.edu.espe.backend.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private ReactiveClaimService reactiveClaimService;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

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
                    emitEvent("USER_REGISTERED", saved.getId(), saved.getName(), saved.getEmail(),
                            "Nuevo usuario registrado: " + saved.getName() + " (" + saved.getEmail() + ")");
                    return new AuthResponseDTO(token);
                });
    }

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

    private void emitEvent(String type, Long entityId, String itemName, String userName, String description) {
        if (reactiveClaimService != null) {
            reactiveClaimService.emitEvent(type, entityId, itemName, userName, null, description);
        }
    }
}
