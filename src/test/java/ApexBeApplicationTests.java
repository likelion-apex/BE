import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Explicit `classes` is required because a test class in the default (unnamed)
// package has no Package object, which breaks SpringBootTestContextBootstrapper's
// upward-package auto-detection of the @SpringBootConfiguration class.
@SpringBootTest(classes = ApexBeApplication.class)
class ApexBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
