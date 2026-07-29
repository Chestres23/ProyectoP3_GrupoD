package ec.edu.espe.backend.service.impl;

import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.reactive.service.ReactiveClaimService;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Autowired(required = false)
    private ReactiveClaimService reactiveClaimService;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<User> save(User user) {
        return userRepository.existsByEmail(user.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new RuntimeException("Email ya registrado"));
                    }
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Mono<Void> deactivate(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado")))
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user);
                })
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        emitEvent("USER_DEACTIVATED", saved.getId(), saved.getName(), saved.getEmail(),
                                "Usuario desactivado: " + saved.getName() + " (" + saved.getEmail() + ")");
                    }
                })
                .then();
    }

    private void emitEvent(String type, Long entityId, String itemName, String userName, String description) {
        if (reactiveClaimService != null) {
            reactiveClaimService.emitEvent(type, entityId, itemName, userName, null, description);
        }
    }
}
