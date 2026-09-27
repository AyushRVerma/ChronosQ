package com.chronosq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.chronosq.security.TestJwtKeyConfiguration;

@Import({TestcontainersConfiguration.class, TestJwtKeyConfiguration.class})
@SpringBootTest
class ChronosqApplicationTests {

	@Test
	void contextLoads() {
	}

}
