package ec.edu.espe.backend.reactive.service;

import ec.edu.espe.backend.reactive.model.ClaimEventType;
import ec.edu.espe.backend.reactive.model.ReactiveClaimEvent;
import ec.edu.espe.backend.reactive.model.ReactiveStatsDTO;
import ec.edu.espe.backend.repository.ClaimRepository;
import ec.edu.espe.backend.repository.LostItemRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReactiveClaimService {

    private final ClaimRepository claimRepository;
    private final LostItemRepository lostItemRepository;

    private final Sinks.Many<ReactiveClaimEvent> eventSink =
            Sinks.many().multicast().onBackpressureBuffer();

    public ReactiveClaimService(ClaimRepository claimRepository,
                                 LostItemRepository lostItemRepository) {
        this.claimRepository = claimRepository;
        this.lostItemRepository = lostItemRepository;
    }

    public void emitEvent(String type, Long entityId, String itemName, String userName, String status) {
        emitEvent(type, entityId, itemName, userName, status, null);
    }

    public void emitEvent(String type, Long entityId, String itemName, String userName, String status, String description) {
        ReactiveClaimEvent event = new ReactiveClaimEvent(
                UUID.randomUUID().toString(),
                ClaimEventType.valueOf(type),
                entityId,
                itemName,
                userName,
                status,
                description
        );
        eventSink.emitNext(event, (signalType, emitResult) ->
                emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED);
    }

    public Flux<ReactiveClaimEvent> getEventStream() {
        return eventSink.asFlux();
    }

    public Mono<ReactiveStatsDTO> computeStatsAsync() {
        return Mono.zip(
                lostItemRepository.count(),
                claimRepository.countByActiveTrue(),
                claimRepository.countByActiveTrueAndStatus("PENDING"),
                claimRepository.countByActiveTrueAndStatus("APPROVED"),
                claimRepository.countByActiveTrueAndStatus("REJECTED"),
                lostItemRepository.countByActiveTrueAndStatus("DELIVERED")
        ).map(tuple -> {
            ReactiveStatsDTO stats = new ReactiveStatsDTO();
            stats.setTotalItems(tuple.getT1());
            stats.setTotalClaims(tuple.getT2());
            stats.setPendingClaims(tuple.getT3());
            stats.setApprovedClaims(tuple.getT4());
            stats.setRejectedClaims(tuple.getT5());
            stats.setDeliveredItems(tuple.getT6());
            long processed = tuple.getT4() + tuple.getT5();
            stats.setApprovalRate(processed > 0 ? (double) tuple.getT4() / processed * 100 : 0.0);
            stats.setTimestamp(LocalDateTime.now());
            return stats;
        });
    }

    public Flux<ReactiveClaimEvent> simulateClaimActivity() {
        AtomicLong counter = new AtomicLong(0);
        String[] items = {"Laptop HP", "Mochila negra", "Celular Samsung", "Llaves", "Calculadora", "Audífonos"};
        String[] users = {"María García", "Carlos López", "Ana Martínez", "Pedro Rojas"};
        ClaimEventType[] types = ClaimEventType.values();

        return Flux.interval(Duration.ofSeconds(3))
                .map(tick -> {
                    long idx = counter.getAndIncrement();
                    String item = items[(int) (idx % items.length)];
                    String user = users[(int) (idx % users.length)];
                    ClaimEventType type = types[(int) (idx % types.length)];
                    return new ReactiveClaimEvent(
                            UUID.randomUUID().toString(),
                            type,
                            1000L + idx,
                            item,
                            user,
                            type == ClaimEventType.CLAIM_APPROVED ? "APPROVED" : type == ClaimEventType.CLAIM_REJECTED ? "REJECTED" : "PENDING",
                            "Simulación: " + type.name() + " - " + item
                    );
                });
    }
}
