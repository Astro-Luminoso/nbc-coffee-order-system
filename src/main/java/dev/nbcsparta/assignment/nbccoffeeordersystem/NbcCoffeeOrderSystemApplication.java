package dev.nbcsparta.assignment.nbccoffeeordersystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NbcCoffeeOrderSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(NbcCoffeeOrderSystemApplication.class, args);
    }

}
