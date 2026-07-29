package ec.edu.espe.backend.controller;

import ec.edu.espe.backend.dto.LostItemRequestDTO;
import ec.edu.espe.backend.dto.LostItemResponseDTO;
import ec.edu.espe.backend.service.LostItemService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ec.edu.espe.backend.service.impl.LostItemAuditService;

import java.util.Map;

@RestController
@RequestMapping("/items")
public class LostItemController {

    private final LostItemService service;
    private final LostItemAuditService auditService;

    public LostItemController(LostItemService service, LostItemAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    // ── CRUD base ──

    @PostMapping
    public Mono<LostItemResponseDTO> createItem(@RequestBody LostItemRequestDTO request) {
        return service.createItem(request);
    }

    @GetMapping
    public Flux<LostItemResponseDTO> getAllItems() {
        return service.getAllActiveItems();
    }

    @GetMapping("/{id}")
    public Mono<LostItemResponseDTO> getItemById(@PathVariable Long id) {
        return service.getItemById(id);
    }

    @PatchMapping("/{id}/claim")
    public Mono<LostItemResponseDTO> claimItem(@PathVariable Long id) {
        return service.claimItem(id);
    }

    @PatchMapping("/{id}/deliver")
    public Mono<LostItemResponseDTO> deliverItem(@PathVariable Long id) {
        return service.deliverItem(id);
    }

    @PutMapping("/{id}")
    public Mono<LostItemResponseDTO> updateItem(@PathVariable Long id,
                                                 @RequestBody LostItemRequestDTO request) {
        return service.updateItem(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteItem(@PathVariable Long id) {
        return service.deleteItem(id);
    }

    // ── Imágenes (WebFlux usa FilePart en vez de MultipartFile) ──

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> uploadImage(@PathVariable Long id,
                                   @RequestPart("file") FilePart file) {
        return service.uploadImage(id, file);
    }

    @GetMapping("/{id}/image")
    public Mono<ResponseEntity<byte[]>> getImage(@PathVariable Long id) {
        return service.getImageBytes(id)
                .flatMap(data -> service.getImageContentType(id)
                        .map(contentType -> ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_TYPE, contentType)
                                .body(data)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    // ── Auditoría reactiva (Práctica 4) ──
    @PostMapping("/auditoria")
    public Mono<ResponseEntity<Map<String, String>>> ejecutarAuditoria() {
        return auditService.ejecutarAuditoria()
                .map(message -> ResponseEntity.ok(Map.of("message", message)));
    }
}