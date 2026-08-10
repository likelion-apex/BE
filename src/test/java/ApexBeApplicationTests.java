import feat.apex_BE.beauty.api.BeautyRoutineController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Explicit `classes` is required because a test class in the default (unnamed)
// package has no Package object, which breaks SpringBootTestContextBootstrapper's
// upward-package auto-detection of the @SpringBootConfiguration class.
@SpringBootTest(classes = ApexBeApplication.class)
class ApexBeApplicationTests {
	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void beautyRoutineApiIsIncludedInProductionApplication() {
		assertThat(applicationContext.getBean(BeautyRoutineController.class)).isNotNull();
	}

}
