package ec.edu.espe.backend.service;

import ec.edu.espe.backend.domain.LostItem;
import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.dto.LostItemRequestDTO;
import ec.edu.espe.backend.exception.InvalidItemStateException;
import ec.edu.espe.backend.exception.UnauthorizedOperationException;
import ec.edu.espe.backend.repository.LostItemRepository;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.security.UserPrincipal;
import ec.edu.espe.backend.service.impl.LostItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostItemServiceTest {

    @Mock
    private LostItemRepository lostItemRepository;

    @Mock
    private UserRepository userRepository;

    private LostItemService service;

    @BeforeEach
    void setUp() {
        service = new LostItemServiceImpl(lostItemRepository, userRepository);
    }

    @Test
    void createItem_shouldSaveAndReturnDto() {
        User reporter = reporterUser(1L, "Maria Gomez", "maria@espe.edu.ec", "USER");
        LostItemRequestDTO request = new LostItemRequestDTO();
        request.setUserId(1L);
        request.setName("Laptop HP");
        request.setDescription("Laptop negra");
        request.setCategory("Electronica");
        request.setLocationFound("Biblioteca");
        request.setDateFound(LocalDate.of(2026, 7, 28));
        request.setImageUrl("/images/laptop.jpg");

        when(userRepository.findById(1L)).thenReturn(Mono.just(reporter));
        when(lostItemRepository.save(any(LostItem.class))).thenAnswer(invocation -> {
            LostItem item = invocation.getArgument(0);
            item.setId(100L);
            return Mono.just(item);
        });

        ArgumentCaptor<LostItem> captor = ArgumentCaptor.forClass(LostItem.class);

        StepVerifier.create(service.createItem(request))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(100L);
                    assertThat(dto.getName()).isEqualTo("Laptop HP");
                    assertThat(dto.getStatus()).isEqualTo("FOUND");
                    assertThat(dto.getReporterName()).isEqualTo("Maria Gomez");
                    assertThat(dto.getReporterId()).isEqualTo(1L);
                })
                .verifyComplete();

        verify(lostItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo("FOUND");
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void getAllActiveItems_shouldMapToDtos() {
        LostItem item = activeItem(10L, 1L, "Mochila negra");
        User reporter = reporterUser(1L, "Maria Gomez", "maria@espe.edu.ec", "USER");

        when(lostItemRepository.findByActiveTrue()).thenReturn(Flux.just(item));
        when(userRepository.findById(1L)).thenReturn(Mono.just(reporter));

        StepVerifier.create(service.getAllActiveItems())
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(10L);
                    assertThat(dto.getName()).isEqualTo("Mochila negra");
                    assertThat(dto.getReporterName()).isEqualTo("Maria Gomez");
                })
                .verifyComplete();
    }

    @Test
    void claimItem_shouldUpdateStatusToClaimed() {
        LostItem item = activeItem(20L, 1L, "Celular");
        User reporter = reporterUser(1L, "Maria Gomez", "maria@espe.edu.ec", "USER");

        when(lostItemRepository.findByIdAndActiveTrue(20L)).thenReturn(Mono.just(item));
        when(lostItemRepository.save(any(LostItem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userRepository.findById(1L)).thenReturn(Mono.just(reporter));

        ArgumentCaptor<LostItem> captor = ArgumentCaptor.forClass(LostItem.class);

        StepVerifier.create(service.claimItem(20L))
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("CLAIMED"))
                .verifyComplete();

        verify(lostItemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLAIMED");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void deliverItem_shouldUpdateStatusToDelivered() {
        LostItem item = activeItem(30L, 1L, "Audifonos");
        item.setStatus("CLAIMED");
        User reporter = reporterUser(1L, "Maria Gomez", "maria@espe.edu.ec", "USER");

        when(lostItemRepository.findByIdAndActiveTrue(30L)).thenReturn(Mono.just(item));
        when(lostItemRepository.save(any(LostItem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userRepository.findById(1L)).thenReturn(Mono.just(reporter));

        ArgumentCaptor<LostItem> captor = ArgumentCaptor.forClass(LostItem.class);

        StepVerifier.create(service.deliverItem(30L))
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("DELIVERED"))
                .verifyComplete();

        verify(lostItemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DELIVERED");
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void claimItem_whenStatusIsNotFound_shouldFailAndNotSave() {
        LostItem item = activeItem(40L, 1L, "Tablet");
        item.setStatus("CLAIMED");

        when(lostItemRepository.findByIdAndActiveTrue(40L)).thenReturn(Mono.just(item));

        StepVerifier.create(service.claimItem(40L))
                .expectError(InvalidItemStateException.class)
                .verify();

        verify(lostItemRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateItem_shouldRejectWhenUserIsNotOwnerNorAdmin() {
        LostItem item = activeItem(50L, 1L, "Billetera");
        User currentUser = reporterUser(2L, "Otro Usuario", "otro@espe.edu.ec", "USER");

        LostItemRequestDTO request = new LostItemRequestDTO();
        request.setName("Billetera actualizada");

        when(lostItemRepository.findByIdAndActiveTrue(50L)).thenReturn(Mono.just(item));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(currentUser),
                currentUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        StepVerifier.create(service.updateItem(50L, request)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .expectError(UnauthorizedOperationException.class)
                .verify();

        verify(lostItemRepository, never()).save(any());
    }

    private LostItem activeItem(Long id, Long userId, String name) {
        LostItem item = new LostItem();
        item.setId(id);
        item.setName(name);
        item.setDescription("Descripcion");
        item.setCategory("General");
        item.setLocationFound("Campus");
        item.setDateFound(LocalDate.of(2026, 7, 28));
        item.setStatus("FOUND");
        item.setActive(true);
        item.setUserId(userId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private User reporterUser(Long id, String name, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword("secret");
        user.setRoleValue(role);
        user.setActive(true);
        return user;
    }
}