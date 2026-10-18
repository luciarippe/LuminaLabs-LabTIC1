package uy.edu.um.luminalabs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Levanta toda la aplicacion (con los datos iniciales y de demo) sobre H2, ver application-test.properties
@SpringBootTest
@ActiveProfiles("test")
class LuminalabsApplicationTests {

	@Test
	void contextLoads() {
	}

}
