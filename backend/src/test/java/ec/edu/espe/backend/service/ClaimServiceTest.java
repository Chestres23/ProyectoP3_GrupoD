package ec.edu.espe.backend.service;

import ec.edu.espe.backend.domain.Claim;
import ec.edu.espe.backend.domain.LostItem;
import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.dto.ClaimRequestDTO;
import ec.edu.espe.backend.dto.ClaimResponseDTO;
import ec.edu.espe.backend.exception.ClaimNotFoundException;
import ec.edu.espe.backend.exception.DuplicateClaimException;
import ec.edu.espe.backend.exception.ItemNotFoundException;
import ec.edu.espe.backend.exception.InvalidClaimStateException;
import ec.edu.espe.backend.exception.UserNotFoundException;
import ec.edu.espe.backend.repository.ClaimRepository;
import ec.edu.espe.backend.repository.LostItemRepository;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.service.impl.ClaimServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para ClaimServiceImpl usando Mockito + StepVerifier.
 * StepVerifier verifica las cadenas reactivas Mono/Flux de forma determinista.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private UserRepository userRepository;
    @Mock private LostItemRepository lostItemRepository;
    @Mock private ObjectProvider<ec.edu.espe.backend.reactive.service.ReactiveClaimService> reactiveProvider;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        when(reactiveProvider.getIfAvailable()).thenReturn(null);
        service = new ClaimServiceImpl(claimRepository, userRepository, lostItemRepository, reactiveProvider);
    }

    private User mockUser() {
        User u = new User();
        u.setId(1L);
        u.setName("Test User");
        u.setEmail("test@espe.edu.ec");
        u.setPassword("pass");
        u.setActive(true);
        return u;
    }

    private LostItem mockItem() {
        LostItem i = new LostItem();
        i.setId(1L);
        i.setName("Bolso azul");
        i.setCategory("Accesorios");
        i.setLocationFound("Biblioteca");
        i.setStatus("FOUND");
        i.setActive(true);
        i.setUserId(1L);
        i.setCreatedAt(LocalDateTime.now());
        i.setUpdatedAt(LocalDateTime.now());
        return i;
    }

    private Claim mockClaim(String status) {
        Claim c = new Claim();
        c.setId(1L);
        c.setUserId(1L);
        c.setItemId(1L);
        c.setObservation("Test");
        c.setStatus(status);
        c.setActive(true);
        c.setClaimDate(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    void shouldCreateClaimSuccessfully() {
        when(claimRepository.existsByUserIdAndItemId(1L, 1L)).thenReturn(Mono.just(false));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser()));
        when(lostItemRepository.findByIdAndActiveTrue(1L)).thenReturn(Mono.just(mockItem()));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            c.setId(1L);
            return Mono.just(c);
        });

        ClaimRequestDTO request = new ClaimRequestDTO();
        request.setUserId(1L);
        request.setItemId(1L);
        request.setObservation("Reclamo válido");

        StepVerifier.create(service.createClaim(request))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                    assertThat(dto.getUserName()).isEqualTo("Test User");
                    assertThat(dto.getItemName()).isEqualTo("Bolso azul");
                })
                .verifyComplete();
    }

    @Test
    void shouldCreateClaimAndUseDependenciesInOrder() {
        when(claimRepository.existsByUserIdAndItemId(1L, 1L)).thenReturn(Mono.just(false));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser()));
        when(lostItemRepository.findByIdAndActiveTrue(1L)).thenReturn(Mono.just(mockItem()));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> {
            Claim claim = inv.getArgument(0);
            claim.setId(42L);
            return Mono.just(claim);
        });

        ClaimRequestDTO request = new ClaimRequestDTO();
        request.setUserId(1L);
        request.setItemId(1L);
        request.setObservation("  Reclamo válido  ");

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);

        StepVerifier.create(service.createClaim(request))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(42L);
                    assertThat(dto.getObservation()).isEqualTo("Reclamo válido");
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();

        InOrder ordered = inOrder(claimRepository, userRepository, lostItemRepository);
        ordered.verify(claimRepository).existsByUserIdAndItemId(1L, 1L);
        ordered.verify(userRepository).findById(1L);
        ordered.verify(lostItemRepository).findByIdAndActiveTrue(1L);
        ordered.verify(claimRepository).save(captor.capture());

        Claim savedClaim = captor.getValue();
        assertThat(savedClaim.getUserId()).isEqualTo(1L);
        assertThat(savedClaim.getItemId()).isEqualTo(1L);
        assertThat(savedClaim.getObservation()).isEqualTo("Reclamo válido");
        assertThat(savedClaim.getStatus()).isEqualTo("PENDING");
        assertThat(savedClaim.getActive()).isTrue();
        assertThat(savedClaim.getClaimDate()).isNotNull();
        assertThat(savedClaim.getCreatedAt()).isNotNull();
        assertThat(savedClaim.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectDuplicateClaim() {
        when(claimRepository.existsByUserIdAndItemId(1L, 1L)).thenReturn(Mono.just(true));

        ClaimRequestDTO request = new ClaimRequestDTO();
        request.setUserId(1L);
        request.setItemId(1L);
        request.setObservation("Duplicado");

        StepVerifier.create(service.createClaim(request))
                .expectError(DuplicateClaimException.class)
                .verify();

        verifyNoInteractions(userRepository, lostItemRepository);
        verify(claimRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenUserDoesNotExist() {
        when(claimRepository.existsByUserIdAndItemId(1L, 1L)).thenReturn(Mono.just(false));
        when(userRepository.findById(1L)).thenReturn(Mono.empty());

        ClaimRequestDTO request = new ClaimRequestDTO();
        request.setUserId(1L);
        request.setItemId(1L);
        request.setObservation("Sin usuario");

        StepVerifier.create(service.createClaim(request))
            .expectError(UserNotFoundException.class)
            .verify();

        verify(lostItemRepository, never()).findByIdAndActiveTrue(anyLong());
        verify(claimRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenItemDoesNotExist() {
        when(claimRepository.existsByUserIdAndItemId(1L, 1L)).thenReturn(Mono.just(false));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser()));
        when(lostItemRepository.findByIdAndActiveTrue(1L)).thenReturn(Mono.empty());

        ClaimRequestDTO request = new ClaimRequestDTO();
        request.setUserId(1L);
        request.setItemId(1L);
        request.setObservation("Sin objeto");

        StepVerifier.create(service.createClaim(request))
            .expectError(ItemNotFoundException.class)
            .verify();

        verify(claimRepository, never()).save(any());
    }

    @Test
    void shouldApprovePendingClaim() {
        Claim pending = mockClaim("PENDING");
        LostItem item = mockItem();
        User user = mockUser();

        when(claimRepository.findById(1L)).thenReturn(Mono.just(pending));
        when(lostItemRepository.findById(1L)).thenReturn(Mono.just(item));
        when(lostItemRepository.save(any())).thenReturn(Mono.just(item));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findById(anyLong())).thenReturn(Mono.just(user));

        StepVerifier.create(service.approve(1L))
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("APPROVED"))
                .verifyComplete();
    }

    @Test
    void shouldNotApproveNonPendingClaim() {
        Claim approved = mockClaim("APPROVED");
        when(claimRepository.findById(1L)).thenReturn(Mono.just(approved));

        StepVerifier.create(service.approve(1L))
                .expectError(InvalidClaimStateException.class)
                .verify();
    }

    @Test
    void shouldRejectPendingClaim() {
        Claim pending = mockClaim("PENDING");
        User user = mockUser();
        LostItem item = mockItem();

        when(claimRepository.findById(1L)).thenReturn(Mono.just(pending));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findById(anyLong())).thenReturn(Mono.just(user));
        when(lostItemRepository.findById(anyLong())).thenReturn(Mono.just(item));

        StepVerifier.create(service.reject(1L))
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("REJECTED"))
                .verifyComplete();
    }

    @Test
    void shouldSoftDeleteClaim() {
        Claim claim = mockClaim("PENDING");
        when(claimRepository.findById(1L)).thenReturn(Mono.just(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.deleteClaim(1L))
                .verifyComplete();
    }

    @Test
    void shouldThrowWhenDeletingNonExistentClaim() {
        when(claimRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteClaim(999L))
                .expectError(ClaimNotFoundException.class)
                .verify();
    }

    @Test
    void shouldReturnAllActiveClaims() {
        Claim claim = mockClaim("PENDING");
        User user = mockUser();
        LostItem item = mockItem();

        when(claimRepository.findAllByActiveTrueOrderByClaimDateDesc()).thenReturn(Flux.just(claim));
        when(userRepository.findById(anyLong())).thenReturn(Mono.just(user));
        when(lostItemRepository.findById(anyLong())).thenReturn(Mono.just(item));

        StepVerifier.create(service.findAll())
                .assertNext(dto -> {
                    assertThat(dto.getObservation()).isEqualTo("Test");
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }
}
