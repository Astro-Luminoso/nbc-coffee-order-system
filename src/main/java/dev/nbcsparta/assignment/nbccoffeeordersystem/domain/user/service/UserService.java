package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 포인트 잔액 변경 기능을 제공하는 서비스이다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 저장소를 사용하는 서비스를 생성한다.
     *
     * @param userRepository 사용자 저장소
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자를 읽기 전용으로 조회한다.
     *
     * @param userId 조회할 사용자 식별자
     * @return 조회된 사용자
     * @throws UserNotFoundException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * 사용자 행을 잠근 상태에서 포인트를 충전하고 갱신된 잔액을 반환한다.
     *
     * @param userId 충전할 사용자 식별자
     * @param amount 충전할 양수 포인트
     * @return 충전 후 포인트 잔액
     * @throws UserNotFoundException 사용자가 존재하지 않는 경우
     */
    @Transactional
    public long charge(long userId, long amount) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.charge(amount);
        return user.getBalance();
    }
}
