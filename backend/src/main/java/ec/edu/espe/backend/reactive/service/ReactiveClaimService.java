package ec.edu.espe.backend.reactive.service;

import ec.edu.espe.backend.reactive.model.ClaimEventType;
import ec.edu.espe.backend.reactive.model.ReactiveClaimEvent;
import ec.edu.espe.backend.reactive.model.ReactiveStatsDTO;
import ec.edu.espe.backend.repository.ClaimRepository;
import ec.edu.espe.backend.repository.LostItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ReactiveClaimService.class);

    private final ClaimRepository claimRepository;
    private final LostItemRepository lostItemRepository;

    private final Sinks.Many<ReactiveClaimEvent> eventSink =
            Sinks.many().multicast().onBackpressureBuffer();
    private final AtomicLong eventCounter = new AtomicLong(0);

    public ReactiveClaimService(ClaimRepository claimRepository,
                                 LostItemRepository lostItemRepository) {
        this.claimRepository = claimRepository;
        this.lostItemRepository = lostItemRepository;
    }

    public void emitEvent(String type, Long entityId, String itemName, String userName, String status) {
        emitEvent(type, entityId, itemName, userName, status, null);
    }

    public void emitEvent(String type, Long entityId, String itemName, String userName, String status, String description) {
        long n = eventCounter.incrementAndGet();
        log.info("emitEvent #{}: type={}, item={}, user={}, desc={}, subscribers={}",
                n, type, itemName, userName, description, eventSink.currentSubscriberCount());
        ReactiveClaimEvent event = new ReactiveClaimEvent(
                UUID.randomUUID().toString(),
                ClaimEventType.valueOf(type),
                entityId,
                itemName,
                userName,
                status,
                description
        );
        Sinks.EmitResult result = eventSink.tryEmitNext(event);
        log.info("emitEvent #{}: tryEmitNext result={}", n, result);
        if (result != Sinks.EmitResult.OK) {
            eventSink.emitNext(event, (signalType, emitResult) -> {
                log.warn("emitEvent #{}: retrying after {} (signalType={})", n, emitResult, signalType);
                return emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED;
            });
        }
    }

    public int subscriberCount() {
        return eventSink.currentSubscriberCount();
    }

    public Flux<ReactiveClaimEvent> getEventStream() {
        return eventSink.asFlux()
                .doOnSubscribe(s -> log.info("SSE subscriber connected. Total subscribers: {}",
                        eventSink.currentSubscriberCount()))
                .doOnCancel(() -> log.info("SSE subscriber cancelled. Remaining: {}",
                        eventSink.currentSubscriberCount()))
                .doOnTerminate(() -> log.info("SSE subscriber terminated. Remaining: {}",
                        eventSink.currentSubscriberCount()));
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
