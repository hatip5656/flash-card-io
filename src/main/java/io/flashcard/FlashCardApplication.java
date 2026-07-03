package io.flashcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlashCardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashCardApplication.class, args);
    }
}
