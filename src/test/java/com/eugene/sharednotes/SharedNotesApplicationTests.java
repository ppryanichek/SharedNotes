package com.eugene.sharednotes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SharedNotesApplicationTests {

    @Test
    void contextLoads() {
    }
}
