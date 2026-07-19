package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User user set user.pointBalance = user.pointBalance + :amount where user.id = :userId")
    int incrementPointBalance(@Param("userId") long userId, @Param("amount") long amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User user
            set user.pointBalance = user.pointBalance - :amount
            where user.id = :userId
              and user.pointBalance >= :amount
            """)
    int deductPointBalanceIfSufficient(@Param("userId") long userId, @Param("amount") long amount);
}
