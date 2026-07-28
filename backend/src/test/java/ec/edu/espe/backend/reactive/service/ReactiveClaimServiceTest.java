package ec.edu.espe.backend.reactive.service;

import ec.edu.espe.backend.reactive.model.ClaimEventType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private LostItemRepository lostItemRepository;

    private ReactiveClaimService service;

    @BeforeEach
    void setUp() {
        service = new ReactiveClaimService(claimRepository, lostItemRepository);
    }

    @Test
    void computeStatsAsync_shouldCombineCountsIntoReactiveSummary() {
        when(lostItemRepository.count()).thenReturn(Mono.just(12L));
        when(claimRepository.countByActiveTrue()).thenReturn(Mono.just(8L));
        when(claimRepository.countByActiveTrueAndStatus("PENDING")).thenReturn(Mono.just(3L));
        when(claimRepository.countByActiveTrueAndStatus("APPROVED")).thenReturn(Mono.just(4L));
        when(claimRepository.countByActiveTrueAndStatus("REJECTED")).thenReturn(Mono.just(1L));
        when(lostItemRepository.countByActiveTrueAndStatus("DELIVERED")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.computeStatsAsync())
                .assertNext(stats -> {
                    assertThat(stats.getTotalItems()).isEqualTo(12L);
                    assertThat(stats.getTotalClaims()).isEqualTo(8L);
                    assertThat(stats.getPendingClaims()).isEqualTo(3L);
                    assertThat(stats.getApprovedClaims()).isEqualTo(4L);
                    assertThat(stats.getRejectedClaims()).isEqualTo(1L);
                    assertThat(stats.getDeliveredItems()).isEqualTo(2L);
                    assertThat(stats.getApprovalRate()).isEqualTo(80.0);
                    assertThat(stats.getTimestamp()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void simulateClaimActivity_shouldEmitFirstEventAfterVirtualDelay() {
        StepVerifier.withVirtualTime(service::simulateClaimActivity)
                .thenAwait(Duration.ofSeconds(3))
                .assertNext(event -> {
                    assertThat(event.getType()).isEqualTo(ClaimEventType.CREATED);
                    assertThat(event.getClaimId()).isEqualTo(1000L);
                    assertThat(event.getItemName()).isNotBlank();
                    assertThat(event.getUserName()).isNotBlank();
                    assertThat(event.getStatus()).isEqualTo("PENDING");
                    assertThat(event.getTimestamp()).isNotNull();
                })
                .thenCancel()
                .verify();
    }
}