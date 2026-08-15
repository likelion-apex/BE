import domain.beauty.api.BeautyRoutineController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Explicit `classes` is required because a test class in the default (unnamed)
// package has no Package object, which breaks SpringBootTestContextBootstrapper's
// upward-package auto-detection of the @SpringBootConfiguration class.
@SpringBootTest(classes = ApexBeApplication.class)
@AutoConfigureMockMvc
class ApexBeApplicationTests {
	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void beautyRoutineApiIsIncludedInProductionApplication() {
		assertThat(applicationContext.getBean(BeautyRoutineController.class)).isNotNull();
	}

	@Test
	void removedKakaoLoginTestPageIsNotPubliclyServed() throws Exception {
		mockMvc.perform(get("/kakao-login-test"))
			.andExpect(status().isUnauthorized());
	}

}
