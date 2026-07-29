package ec.edu.espe.backend.service.impl;

import ec.edu.espe.backend.domain.Claim;
import ec.edu.espe.backend.dto.ClaimRequestDTO;
import ec.edu.espe.backend.dto.ClaimResponseDTO;
import ec.edu.espe.backend.exception.*;
import ec.edu.espe.backend.reactive.service.ReactiveClaimService;
import ec.edu.espe.backend.repository.ClaimRepository;
import ec.edu.espe.backend.repository.LostItemRepository;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.service.ClaimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final LostItemRepository lostItemRepository;

    @Autowired(required = false)
    private ReactiveClaimService reactiveClaimService;

    public ClaimServiceImpl(ClaimRepository claimRepository,
                            UserRepository userRepository,
                            LostItemRepository lostItemRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.lostItemRepository = lostItemRepository;
    }

    @Override
    public Mono<ClaimResponseDTO> createClaim(ClaimRequestDTO request) {
        return claimRepository.existsByUserIdAndItemId(request.getUserId(), request.getItemId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateClaimException("El usuario ya registró un reclamo para este objeto."));
                    }
                    return userRepository.findById(request.getUserId())
                            .switchIfEmpty(Mono.error(new UserNotFoundException("Usuario no encontrado.")));
                })
                .flatMap(user ->
                    lostItemRepository.findByIdAndActiveTrue(request.getItemId())
                            .switchIfEmpty(Mono.error(new ItemNotFoundException("Objeto no encontrado o inactivo.")))
                            .flatMap(item -> {
                                if (!"FOUND".equals(item.getStatus())) {
                                    return Mono.error(new InvalidClaimStateException("Solo se puede reclamar un objeto en estado FOUND."));
                                }
                                Claim claim = new Claim();
                                claim.setUserId(user.getId());
                                claim.setItemId(item.getId());
                                claim.setObservation(request.getObservation().trim());
                                claim.setStatus("PENDING");
                                LocalDateTime now = LocalDateTime.now();
                                claim.setClaimDate(now);
                                claim.setCreatedAt(now);
                                claim.setUpdatedAt(now);
                                return claimRepository.save(claim)
                                        .map(saved -> {
                                            emitEvent("CLAIM_CREATED", saved.getId(), item.getName(), user.getName(),
                                                    "Nuevo reclamo: " + item.getName() + " por " + user.getName());
                                            return buildDTO(saved, user.getId(), user.getName(), user.getEmail(),
                                                    item.getId(), item.getName(), item.getStatus(),
                                                    item.getCategory(), item.getLocationFound());
                                        });
                            })
                );
    }

    @Override
    public Flux<ClaimResponseDTO> findAll() {
        return claimRepository.findAllByActiveTrueOrderByClaimDateDesc()
                .concatMap(this::enrichClaimDTO);
    }

    @Override
    public Mono<ClaimResponseDTO> approve(Long id) {
        return claimRepository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException("Reclamo no encontrado.")))
                .flatMap(claim -> {
                    if (!"PENDING".equals(claim.getStatus())) {
                        return Mono.error(new InvalidClaimStateException("Solo se puede aprobar un reclamo pendiente."));
                    }
                    return lostItemRepository.findById(claim.getItemId())
                            .flatMap(item -> {
                                if (!"FOUND".equals(item.getStatus())) {
                                    return Mono.error(new InvalidClaimStateException("El objeto ya fue reclamado o entregado."));
                                }
                                claim.setStatus("APPROVED");
                                claim.setUpdatedAt(LocalDateTime.now());
                                item.setStatus("CLAIMED");
                                item.setUpdatedAt(LocalDateTime.now());
                                return lostItemRepository.save(item)
                                        .then(claimRepository.save(claim))
                                        .flatMap(saved -> enrichClaimDTO(saved)
                                                .doOnNext(dto -> emitEvent("CLAIM_APPROVED", saved.getId(), item.getName(), null,
                                                        "Reclamo aprobado: " + item.getName())));
                            });
                });
    }

    @Override
    public Mono<ClaimResponseDTO> reject(Long id) {
        return claimRepository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException("Reclamo no encontrado.")))
                .flatMap(claim -> {
                    if (!"PENDING".equals(claim.getStatus())) {
                        return Mono.error(new InvalidClaimStateException("Solo se puede rechazar un reclamo pendiente."));
                    }
                    claim.setStatus("REJECTED");
                    claim.setUpdatedAt(LocalDateTime.now());
                    return claimRepository.save(claim)
                            .flatMap(saved -> enrichClaimDTO(saved)
                                    .doOnNext(dto -> emitEvent("CLAIM_REJECTED", saved.getId(), dto.getItemName(), null,
                                            "Reclamo rechazado: " + dto.getItemName())));
                });
    }

    @Override
    public Mono<Void> deleteClaim(Long id) {
        return claimRepository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException("Reclamo no encontrado.")))
                .flatMap(claim -> {
                    claim.setActive(false);
                    claim.setUpdatedAt(LocalDateTime.now());
                    return claimRepository.save(claim)
                            .doOnNext(saved -> emitEvent("CLAIM_DELETED", saved.getId(), null, null,
                                    "Reclamo eliminado #" + saved.getId()));
                })
                .then();
    }

    private Mono<ClaimResponseDTO> enrichClaimDTO(Claim claim) {
        Mono<ec.edu.espe.backend.domain.User> userMono = userRepository.findById(claim.getUserId())
                .defaultIfEmpty(new ec.edu.espe.backend.domain.User());
        Mono<ec.edu.espe.backend.domain.LostItem> itemMono = lostItemRepository.findById(claim.getItemId())
                .defaultIfEmpty(new ec.edu.espe.backend.domain.LostItem());
        return Mono.zip(userMono, itemMono)
                .map(tuple -> buildDTO(claim,
                        tuple.getT1().getId(), tuple.getT1().getName(), tuple.getT1().getEmail(),
                        tuple.getT2().getId(), tuple.getT2().getName(), tuple.getT2().getStatus(),
                        tuple.getT2().getCategory(), tuple.getT2().getLocationFound()));
    }

    private ClaimResponseDTO buildDTO(Claim claim, Long userId, String userName, String userEmail,
                                       Long itemId, String itemName, String itemStatus,
                                       String itemCategory, String itemLocation) {
        ClaimResponseDTO dto = new ClaimResponseDTO();
        dto.setId(claim.getId());
        dto.setClaimDate(claim.getClaimDate());
        dto.setObservation(claim.getObservation());
        dto.setStatus(claim.getStatus());
        dto.setUserId(userId);
        dto.setUserName(userName);
        dto.setUserEmail(userEmail);
        dto.setItemId(itemId);
        dto.setItemName(itemName);
        dto.setItemStatus(itemStatus);
        dto.setItemCategory(itemCategory);
        dto.setItemLocation(itemLocation);
        return dto;
    }

    private void emitEvent(String type, Long entityId, String itemName, String userName, String description) {
        if (reactiveClaimService != null) {
            reactiveClaimService.emitEvent(type, entityId, itemName, userName, null, description);
        }
    }
}
