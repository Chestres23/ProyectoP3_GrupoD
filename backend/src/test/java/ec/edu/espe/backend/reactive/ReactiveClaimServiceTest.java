package ec.edu.espe.backend.reactive;

import ec.edu.espe.backend.reactive.model.ClaimEventType;
import ec.edu.espe.backend.reactive.model.ReactiveClaimEvent;
import ec.edu.espe.backend.reactive.model.ReactiveStatsDTO;
import ec.edu.espe.backend.reactive.service.ReactiveClaimService;
import ec.edu.espe.backend.repository.ClaimRepository;
import ec.edu.espe.backend.repository.LostItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private LostItemRepository lostItemRepository;

    private ReactiveClaimService service;

    @BeforeEach
    void setUp() {
        service = new ReactiveClaimService(claimRepository, lostItemRepository);
    }

    @Test
    void hotStreamShouldEmitEvents() {
        StepVerifier.create(service.getEventStream().take(1))
                .then(() -> service.emitEvent("CLAIM_CREATED", 1L, "Laptop", "María", "PENDING"))
                .assertNext(event -> {
                    assertThat(event.getType()).isEqualTo(ClaimEventType.CLAIM_CREATED);
                    assertThat(event.getEntityId()).isEqualTo(1L);
                    assertThat(event.getItemName()).isEqualTo("Laptop");
                    assertThat(event.getUserName()).isEqualTo("María");
                })
                .verifyComplete();
    }

    @Test
    void hotStreamShouldEmitMultipleEvents() {
        StepVerifier.create(service.getEventStream().take(3))
                .then(() -> {
                    service.emitEvent("CLAIM_CREATED", 1L, "Laptop", "María", "PENDING");
                    service.emitEvent("CLAIM_APPROVED", 2L, "Mochila", "Carlos", "APPROVED");
                    service.emitEvent("CLAIM_REJECTED", 3L, "Llaves", "Ana", "REJECTED");
                })
                .assertNext(e -> assertThat(e.getType()).isEqualTo(ClaimEventType.CLAIM_CREATED))
                .assertNext(e -> assertThat(e.getType()).isEqualTo(ClaimEventType.CLAIM_APPROVED))
                .assertNext(e -> assertThat(e.getType()).isEqualTo(ClaimEventType.CLAIM_REJECTED))
                .verifyComplete();
    }

    @Test
    void computeStatsAsyncShouldReturnValidStats() {
        when(lostItemRepository.count()).thenReturn(Mono.just(10L));
        when(claimRepository.countByActiveTrue()).thenReturn(Mono.just(5L));
        when(claimRepository.countByActiveTrueAndStatus("PENDING")).thenReturn(Mono.just(2L));
        when(claimRepository.countByActiveTrueAndStatus("APPROVED")).thenReturn(Mono.just(2L));
        when(claimRepository.countByActiveTrueAndStatus("REJECTED")).thenReturn(Mono.just(1L));
        when(lostItemRepository.countByActiveTrueAndStatus("DELIVERED")).thenReturn(Mono.just(3L));

        StepVerifier.create(service.computeStatsAsync())
                .assertNext(stats -> {
                    assertThat(stats.getTotalItems()).isEqualTo(10);
                    assertThat(stats.getTotalClaims()).isEqualTo(5);
                    assertThat(stats.getPendingClaims()).isEqualTo(2);
                    assertThat(stats.getApprovedClaims()).isEqualTo(2);
                    assertThat(stats.getRejectedClaims()).isEqualTo(1);
                    assertThat(stats.getDeliveredItems()).isEqualTo(3);
                    assertThat(stats.getApprovalRate()).isGreaterThan(60.0);
                    assertThat(stats.getTimestamp()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void simulateClaimActivityShouldGeneratePeriodicEvents() {
        StepVerifier.create(service.simulateClaimActivity().take(3))
                .assertNext(e -> {
                    assertThat(e.getEventId()).isNotNull();
                    assertThat(e.getEntityId()).isGreaterThanOrEqualTo(1000L);
                    assertThat(e.getItemName()).isNotBlank();
                    assertThat(e.getUserName()).isNotBlank();
                    assertThat(e.getDescription()).contains("Simulación");
                })
                .assertNext(e -> assertThat(e.getEntityId()).isEqualTo(1001L))
                .assertNext(e -> assertThat(e.getEntityId()).isEqualTo(1002L))
                .thenCancel()
                .verify(Duration.ofSeconds(15));
    }
}
