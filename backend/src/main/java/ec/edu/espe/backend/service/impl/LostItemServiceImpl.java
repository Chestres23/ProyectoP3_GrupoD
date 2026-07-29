package ec.edu.espe.backend.service.impl;

import ec.edu.espe.backend.domain.LostItem;
import ec.edu.espe.backend.dto.LostItemRequestDTO;
import ec.edu.espe.backend.dto.LostItemResponseDTO;
import ec.edu.espe.backend.exception.InvalidItemStateException;
import ec.edu.espe.backend.exception.ItemNotFoundException;
import ec.edu.espe.backend.exception.UnauthorizedOperationException;
import ec.edu.espe.backend.repository.LostItemRepository;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.security.UserPrincipal;
import ec.edu.espe.backend.service.LostItemService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Implementación reactiva del servicio de objetos perdidos.
 * Usa ReactiveSecurityContextHolder para obtener el usuario autenticado
 * (reemplaza SecurityContextHolder que es bloqueante/thread-local).
 */
@Service
public class LostItemServiceImpl implements LostItemService {

    private final LostItemRepository itemRepository;
    private final UserRepository userRepository;

    public LostItemServiceImpl(LostItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<LostItemResponseDTO> createItem(LostItemRequestDTO request) {
        return userRepository.findById(request.getUserId())
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado")))
                .flatMap(user -> {
                    LostItem item = new LostItem();
                    item.setName(request.getName());
                    item.setDescription(request.getDescription());
                    item.setCategory(request.getCategory());
                    item.setLocationFound(request.getLocationFound());
                    item.setDateFound(request.getDateFound());
                    item.setImageUrl(request.getImageUrl());
                    item.setUserId(user.getId());
                    LocalDateTime now = LocalDateTime.now();
                    item.setCreatedAt(now);
                    item.setUpdatedAt(now);
                    return itemRepository.save(item)
                            .map(saved -> mapToDTO(saved, user.getName(), user.getId()));
                });
    }

    @Override
    public Flux<LostItemResponseDTO> getAllActiveItems() {
        // Para cada item, resolvemos el nombre del usuario de forma reactiva.
        // Usamos concatMap para no disparar demasiadas consultas concurrentes que puedan romper
        // el streaming de la respuesta en conexiones con muchos resultados.
        return itemRepository.findByActiveTrue()
                .concatMap(item -> userRepository.findById(item.getUserId())
                        .map(user -> mapToDTO(item, user.getName(), user.getId()))
                        .defaultIfEmpty(mapToDTO(item, "Desconocido", item.getUserId())));
    }

    @Override
    public Mono<LostItemResponseDTO> getItemById(Long id) {
        return getActiveItem(id)
                .flatMap(item -> userRepository.findById(item.getUserId())
                        .map(user -> mapToDTO(item, user.getName(), user.getId()))
                        .defaultIfEmpty(mapToDTO(item, "Desconocido", item.getUserId())));
    }

    @Override
    public Mono<LostItemResponseDTO> claimItem(Long id) {
        return getActiveItem(id)
                .flatMap(item -> {
                    if (!"FOUND".equals(item.getStatus())) {
                        return Mono.error(new InvalidItemStateException("El objeto ya fue reclamado o entregado."));
                    }
                    item.setStatus("CLAIMED");
                    item.setUpdatedAt(LocalDateTime.now());
                    return itemRepository.save(item);
                })
                .flatMap(this::enrichDTO);
    }

    @Override
    public Mono<LostItemResponseDTO> deliverItem(Long id) {
        return getActiveItem(id)
                .flatMap(item -> {
                    if (!"CLAIMED".equals(item.getStatus())) {
                        return Mono.error(new InvalidItemStateException("El objeto debe ser reclamado antes de entregarse."));
                    }
                    item.setStatus("DELIVERED");
                    item.setActive(false);
                    item.setUpdatedAt(LocalDateTime.now());
                    return itemRepository.save(item);
                })
                .flatMap(this::enrichDTO);
    }

    @Override
    public Mono<LostItemResponseDTO> updateItem(Long id, LostItemRequestDTO request) {
        return getActiveItem(id)
                .flatMap(item -> getAuthenticatedUser()
                        .flatMap(currentUser -> {
                            boolean isAdmin = "ADMIN".equals(currentUser.getRoleValue());
                            if (!isAdmin && !currentUser.getId().equals(item.getUserId())) {
                                return Mono.error(new UnauthorizedOperationException(
                                        "Solo el creador del objeto o un administrador puede editarlo."));
                            }
                            if (request.getName() != null && !request.getName().isBlank()) {
                                item.setName(request.getName());
                            }
                            if (request.getDescription() != null) {
                                item.setDescription(request.getDescription());
                            }
                            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                                item.setCategory(request.getCategory());
                            }
                            if (request.getLocationFound() != null) {
                                item.setLocationFound(request.getLocationFound());
                            }
                            if (request.getDateFound() != null) {
                                item.setDateFound(request.getDateFound());
                            }
                            if (request.getImageUrl() != null) {
                                item.setImageUrl(request.getImageUrl());
                            }
                            item.setUpdatedAt(LocalDateTime.now());
                            return itemRepository.save(item);
                        }))
                .flatMap(this::enrichDTO);
    }

    @Override
    public Mono<Void> deleteItem(Long id) {
        return getActiveItem(id)
                .flatMap(item -> {
                    item.setActive(false);
                    item.setUpdatedAt(LocalDateTime.now());
                    return itemRepository.save(item);
                })
                .then();
    }

    /**
     * Sube una imagen usando FilePart (WebFlux) en vez de MultipartFile (Servlet).
     * Lee los bytes de forma reactiva con DataBufferUtils.
     */
    @Override
    public Mono<Void> uploadImage(Long id, FilePart filePart) {
        return getActiveItem(id)
                .flatMap(item ->
                    DataBufferUtils.join(filePart.content())
                            .flatMap(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                item.setImageData(bytes);
                                item.setImageType(filePart.headers().getContentType() != null
                                        ? filePart.headers().getContentType().toString()
                                        : "image/jpeg");
                                item.setUpdatedAt(LocalDateTime.now());
                                return itemRepository.save(item).then();
                            })
                );
    }

    @Override
    public Mono<byte[]> getImageBytes(Long id) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new ItemNotFoundException("Objeto no encontrado.")))
                .flatMap(item -> {
                    byte[] data = item.getImageData();
                    if (data == null) return Mono.empty();
                    return Mono.just(data);
                });
    }

    @Override
    public Mono<String> getImageContentType(Long id) {
        return itemRepository.findById(id)
                .map(item -> item.getImageType() != null ? item.getImageType() : "image/jpeg")
                .defaultIfEmpty("image/jpeg");
    }

    // ── Helpers ──

    private Mono<LostItem> getActiveItem(Long id) {
        return itemRepository.findByIdAndActiveTrue(id)
                .switchIfEmpty(Mono.error(new ItemNotFoundException("Objeto no encontrado o inactivo.")));
    }

    /**
     * Obtiene el usuario autenticado desde ReactiveSecurityContextHolder
     * (reemplaza SecurityContextHolder que es thread-local/bloqueante).
     */
    private Mono<ec.edu.espe.backend.domain.User> getAuthenticatedUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (UserPrincipal) ctx.getAuthentication().getPrincipal())
                .map(UserPrincipal::getUser);
    }

    private Mono<LostItemResponseDTO> enrichDTO(LostItem item) {
        return userRepository.findById(item.getUserId())
                .map(user -> mapToDTO(item, user.getName(), user.getId()))
                .defaultIfEmpty(mapToDTO(item, "Desconocido", item.getUserId()));
    }

    private LostItemResponseDTO mapToDTO(LostItem item, String reporterName, Long reporterId) {
        LostItemResponseDTO dto = new LostItemResponseDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setCategory(item.getCategory());
        dto.setLocationFound(item.getLocationFound());
        dto.setDateFound(item.getDateFound());
        dto.setStatus(item.getStatus());
        dto.setImageUrl(item.getImageUrl());
        dto.setHasImage(item.getImageData() != null && item.getImageData().length > 0);
        dto.setReporterName(reporterName);
        dto.setReporterId(reporterId);
        return dto;
    }
}