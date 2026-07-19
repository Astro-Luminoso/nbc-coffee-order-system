package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 결제가 완료된 단일 메뉴 주문을 표현한다.
 */
@Entity
@Table(
        name = "coffee_order",
        indexes = {
                @Index(name = "idx_coffee_order_ordered_at_menu_id", columnList = "ordered_at,menu_id"),
                @Index(
                        name = "idx_coffee_order_projection_status_ordered_at_id",
                        columnList = "popularity_projection_status,ordered_at,id"
                )
        }
)
public class CoffeeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "payment_amount", nullable = false, columnDefinition = "BIGINT CHECK (payment_amount > 0)")
    private long paymentAmount;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_status", nullable = false, length = 16)
    private CollectionStatus collectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "popularity_projection_status", nullable = false, length = 16)
    private PopularityProjectionStatus popularityProjectionStatus;

    protected CoffeeOrder() {
    }

    /**
     * 결제 완료된 단일 메뉴 주문을 생성한다.
     *
     * @param user 주문한 사용자
     * @param menu 주문한 메뉴
     * @param paymentAmount 결제한 포인트
     * @param orderedAt 결제 완료 시각
     */
    public CoffeeOrder(User user, Menu menu, long paymentAmount, Instant orderedAt) {
        if (paymentAmount <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }
        this.user = user;
        this.menu = menu;
        this.paymentAmount = paymentAmount;
        this.orderedAt = orderedAt;
        this.collectionStatus = CollectionStatus.PENDING;
        this.popularityProjectionStatus = PopularityProjectionStatus.PENDING;
    }

    /**
     * 주문 식별자를 반환한다.
     *
     * @return 주문 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 주문한 사용자 식별자를 반환한다.
     *
     * @return 사용자 식별자
     */
    public long getUserId() {
        return user.getId();
    }

    /**
     * 주문한 메뉴 식별자를 반환한다.
     *
     * @return 메뉴 식별자
     */
    public long getMenuId() {
        return menu.getId();
    }

    /**
     * 결제 금액을 반환한다.
     *
     * @return 결제 금액
     */
    public long getPaymentAmount() {
        return paymentAmount;
    }

    /**
     * 주문 완료 시각을 반환한다.
     *
     * @return 주문 완료 시각
     */
    public Instant getOrderedAt() {
        return orderedAt;
    }

    /**
     * 외부 데이터 수집 전송 상태를 반환한다.
     *
     * @return 수집 전송 상태
     */
    public CollectionStatus getCollectionStatus() {
        return collectionStatus;
    }

    /**
     * 외부 데이터 수집 전송이 완료됐는지 반환한다.
     *
     * @return 전송 완료 여부
     */
    public boolean isCollectionDelivered() {
        return collectionStatus == CollectionStatus.SUCCEEDED;
    }

    /**
     * Redis 인기 메뉴 투영 상태를 반환한다.
     *
     * @return 인기 메뉴 투영 상태
     */
    public PopularityProjectionStatus getPopularityProjectionStatus() {
        return popularityProjectionStatus;
    }

    /**
     * Redis 인기 메뉴 투영이 완료됐는지 반환한다.
     *
     * @return 인기 메뉴 투영 완료 여부
     */
    public boolean isPopularityProjected() {
        return popularityProjectionStatus == PopularityProjectionStatus.SUCCEEDED;
    }

    /**
     * 외부 수집 플랫폼이 주문 데이터를 정상적으로 수신했음을 기록한다.
     */
    public void markCollectionSucceeded() {
        this.collectionStatus = CollectionStatus.SUCCEEDED;
    }

    /**
     * 주문의 Redis 인기 메뉴 투영이 완료되었음을 기록한다.
     */
    public void markPopularityProjectionSucceeded() {
        this.popularityProjectionStatus = PopularityProjectionStatus.SUCCEEDED;
    }
}
