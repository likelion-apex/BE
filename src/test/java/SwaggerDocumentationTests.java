import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApexBeApplication.class, properties = "features.kakao-login-test.enabled=true")
@AutoConfigureMockMvc
class SwaggerDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentsEveryProductionApiWithExpectedSecurityAndResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.length()").value(7))
			.andExpect(jsonPath("$.paths['/api/auth/kakao/login'].post.summary").value("카카오 로그인/회원가입"))
			.andExpect(jsonPath("$.paths['/api/auth/kakao/login'].post.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/auth/reissue'].post.responses['401']").exists())
			.andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/members/me'].get.responses['404']").exists())
			.andExpect(jsonPath("$.paths['/api/cosmetics/info'].post.responses['400']").exists())
			.andExpect(jsonPath("$.paths['/api/cosmetics/ingredients/{name}'].get.summary").value("성분 단건 조회"))
			.andExpect(jsonPath("$.paths['/api/v1/beauty-routines/analyze'].post.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/beauty-routines/analyze'].post.responses['503']").exists())
			.andExpect(jsonPath("$.components.schemas.AnalyzeBeautyRoutineRequest.properties.youtubeUrl.example")
				.value("https://www.youtube.com/shorts/-PC1SkLxtvo"));
	}

	@Test
	void servesKakaoLoginTestPageWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/kakao-login-test"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("카카오 로그인 테스트")));
	}
}
