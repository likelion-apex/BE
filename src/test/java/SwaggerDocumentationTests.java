import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
			.andExpect(jsonPath("$.paths.length()").value(24))
			.andExpect(jsonPath("$.paths['/api/auth/kakao/login'].post.summary").value("카카오 로그인/회원가입"))
			.andExpect(jsonPath("$.paths['/api/auth/kakao/login'].post.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/auth/reissue'].post.responses['401']").exists())
			.andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/members/me'].get.responses['404']").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/members/me'].get.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/members/me'].patch.summary").value("프로필 통합 수정"))
			.andExpect(jsonPath("$.paths['/api/members/me/nickname'].patch.summary").value("닉네임 변경"))
			.andExpect(jsonPath("$.paths['/api/members/me/skin-type'].patch.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/members/me/skin-concerns'].patch.summary").value("피부 고민 변경"))
			.andExpect(jsonPath("$.components.schemas.MemberResponse.properties.skinType.enum").isArray())
			.andExpect(jsonPath("$.paths['/api/cosmetics/info'].post.responses['400']").exists())
			.andExpect(jsonPath("$.paths['/api/cosmetics/ingredients/{name}'].get.summary").value("성분 단건 조회"))
			.andExpect(jsonPath("$.paths['/api/v1/beauty-routines/analyze'].post.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/beauty-routines/analyze'].post.responses['503']").exists())
			.andExpect(jsonPath("$.components.schemas.AnalyzeBeautyRoutineRequest.properties.youtubeUrl.example")
				.value("https://www.youtube.com/shorts/-PC1SkLxtvo"))
			.andExpect(jsonPath("$.paths['/api/v1/products/search'].get.summary").value("화장품 검색"))
			.andExpect(jsonPath("$.paths['/api/v1/products/search'].get.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/inventory'].get.summary").value("인벤토리 전체 조회"))
			.andExpect(jsonPath("$.paths['/api/v1/inventory'].post.summary").value("인벤토리 추가"))
			.andExpect(jsonPath("$.paths['/api/v1/inventory'].post.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/inventory/favorites'].get.summary").value("즐겨찾는 화장품 노출"))
			.andExpect(jsonPath("$.paths['/api/v1/inventory/{inventoryId}'].delete.summary").value("인벤토리 삭제"))
			.andExpect(jsonPath("$.paths['/api/v1/inventory/{inventoryId}/favorite'].patch.summary").value("즐겨찾기 등록/해제"))
			.andExpect(jsonPath("$.paths['/api/v1/inventory/{inventoryId}/ai-analysis'].get.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/inventory/{inventoryId}/ingredients'].get.summary").value("성분 분석"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses'].post.summary").value("전체 스킨케어 루틴 분석 요청"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses'].get.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/shortform-analyses/{analysisId}/status'].get.summary").value("루틴 분석 진행 상태 조회"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses/{analysisId}/cancel'].post.summary").value("루틴 분석 취소"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses/{analysisId}/results/{resultId}'].get.summary").value("루틴 단계별 제품 분석 상세 조회"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses/{analysisId}/optimize'].post.summary").value("내 인벤토리 기반 루틴 최적화"))
			.andExpect(jsonPath("$.paths['/api/shortform-analyses/{analysisId}/apply'].post.summary").value("분석한 루틴 적용 또는 보관"));
	}

	@Test
	void servesKakaoLoginTestPageWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/kakao-login-test"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("카카오 로그인 테스트")))
			.andExpect(content().string(containsString("서버에서 자동으로 불러옴")))
			.andExpect(content().string(not(containsString("__KAKAO_CLIENT_ID_BASE64__"))))
			.andExpect(content().string(not(containsString("__KAKAO_REDIRECT_URI_BASE64__"))))
			.andExpect(content().string(not(containsString("id=\"client-id\""))))
			.andExpect(content().string(not(containsString("id=\"backend-url\""))))
			.andExpect(content().string(containsString("AI 전체 스킨케어 루틴 분석")))
			.andExpect(content().string(containsString("https://www.youtube.com/shorts/t1S24pgO2XQ")))
			.andExpect(content().string(containsString("id=\"analysis-feedback\"")))
			.andExpect(content().string(containsString("분석 요청 중…")))
			.andExpect(content().string(containsString("YOUTUBE_API_KEY 등록을 요청해 주세요")))
			.andExpect(content().string(containsString("data-save-type=\"TODAY\"")));
	}
}
