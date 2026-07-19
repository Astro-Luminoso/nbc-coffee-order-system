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
@Table(name = "coffee_order", indexes = @Index(name = "idx_coffee_order_ordered_at_menu_id", columnList = "ordered_at,menu_id"))
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

    protected CoffeeOrder() {
    }

    public CoffeeOrder(User user, Menu menu, long paymentAmount, Instant orderedAt) {
        if (paymentAmount <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }
        this.user = user;
        this.menu = menu;
        this.paymentAmount = paymentAmount;
        this.orderedAt = orderedAt;
        this.collectionStatus = CollectionStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return user.getId();
    }

    public long getMenuId() {
        return menu.getId();
    }

    public long getPaymentAmount() {
        return paymentAmount;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public CollectionStatus getCollectionStatus() {
        return collectionStatus;
    }

    public boolean isCollectionDelivered() {
        return collectionStatus == CollectionStatus.SUCCEEDED;
    }

    /**
     * 외부 수집 플랫폼이 주문 데이터를 정상적으로 수신했음을 기록한다.
     */
    public void markCollectionSucceeded() {
        this.collectionStatus = CollectionStatus.SUCCEEDED;
    }
}
