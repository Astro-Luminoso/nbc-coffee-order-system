package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.UserNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사용자 포인트 충전 서비스의 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    /**
     * DB 원자 증가 연산으로 사용자 잔액을 충전하는지 검증한다.
     */
    @Test
    void chargeUsesAtomicIncrementAndReturnsUpdatedBalance() {
        User user = new User(100L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.incrementPointBalance(1L, 50L)).thenAnswer(invocation -> {
            user.charge(invocation.getArgument(1));
            return 1;
        });

        long balance = userService.charge(1L, 50L);

        assertThat(balance).isEqualTo(150L);
        verify(userRepository).incrementPointBalance(1L, 50L);
    }

    /**
     * 존재하지 않는 사용자는 사용자 미존재 예외를 발생시키는지 검증한다.
     */
    @Test
    void chargeThrowsUserNotFoundExceptionWhenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.charge(1L, 50L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
