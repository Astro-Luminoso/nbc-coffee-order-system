package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 엔티티의 영속성 접근을 담당한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 포인트 변경을 위해 사용자 행을 배타적으로 잠근 뒤 조회한다.
     *
     * @param userId 조회할 사용자 식별자
     * @return 잠긴 사용자 엔티티
    */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
