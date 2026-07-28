package ec.edu.espe.backend.service;

import ec.edu.espe.backend.domain.User;
import ec.edu.espe.backend.repository.UserRepository;
import ec.edu.espe.backend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository);
    }

    @Test
    void save_shouldPersistUser_whenEmailIsUnique() {
        User user = new User();
        user.setName("Ana Lopez");
        user.setEmail("ana@espe.edu.ec");

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        StepVerifier.create(service.save(user))
                .assertNext(saved -> assertThat(saved.getEmail()).isEqualTo("ana@espe.edu.ec"))
                .verifyComplete();

        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(captor.getValue().getRoleValue()).isEqualTo("USER");
    }

    @Test
    void save_duplicateEmail_shouldFail_andNotSave() {
        User user = new User();
        user.setName("Ana Lopez");
        user.setEmail("ana@espe.edu.ec");

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(Mono.just(true));

        StepVerifier.create(service.save(user))
                .expectErrorMessage("Email ya registrado")
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivate_shouldMarkUserInactive_andSave() {
        User user = new User();
        user.setId(2L);
        user.setName("Ana Lopez");
        user.setEmail("ana@espe.edu.ec");
        user.setActive(true);

        when(userRepository.findById(2L)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.deactivate(2L))
                .verifyComplete();

        verify(userRepository).save(user);
        assertThat(user.getActive()).isFalse();
    }
}