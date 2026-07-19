package dev.nbcsparta.assignment.nbccoffeeordersystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 커피 주문 시스템 애플리케이션을 시작하고 스케줄 작업을 활성화한다.
 */
@SpringBootApplication
@EnableScheduling
public class NbcCoffeeOrderSystemApplication {

    /**
     * 스프링 부트 애플리케이션을 실행한다.
     *
     * @param args 실행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(NbcCoffeeOrderSystemApplication.class, args);
    }

}
