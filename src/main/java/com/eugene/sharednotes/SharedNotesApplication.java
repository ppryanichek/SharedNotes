package com.eugene.sharednotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SharedNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharedNotesApplication.class, args);
    }

}
