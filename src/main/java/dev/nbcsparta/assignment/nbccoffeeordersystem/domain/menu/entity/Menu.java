package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 주문 가능한 커피 메뉴를 표현하는 영속 엔티티이다.
 */
@Entity
@Table(name = "menu")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "BIGINT CHECK (price > 0)")
    private long price;

    /**
     * JPA가 엔티티를 생성할 때 사용하는 생성자이다.
     */
    protected Menu() {
    }

    /**
     * 메뉴 이름과 가격으로 메뉴를 생성한다.
     *
     * @param name 메뉴 이름
     * @param price 메뉴 가격
     */
    public Menu(String name, long price) {
        this.name = name;
        this.price = price;
    }

    /**
     * 메뉴 식별자를 반환한다.
     *
     * @return 메뉴 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 메뉴 이름을 반환한다.
     *
     * @return 메뉴 이름
     */
    public String getName() {
        return name;
    }

    /**
     * 메뉴 가격을 반환한다.
     *
     * @return 메뉴 가격
     */
    public long getPrice() {
        return price;
    }

}
