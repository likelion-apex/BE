package config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Hidden
public class KakaoLoginTestPageController {

    private static final Resource TEST_PAGE = new ClassPathResource("kakao-login-test/index.html");

    private final boolean enabled;
    private final String clientId;
    private final String redirectUri;

    public KakaoLoginTestPageController(
            @Value("${features.kakao-login-test.enabled:false}") boolean enabled,
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.redirect-uri}") String redirectUri
    ) {
        this.enabled = enabled;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    @GetMapping(value = {"/kakao-login-test", "/kakao-login-test/"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> page() throws IOException {
        if (!enabled) {
            return ResponseEntity.notFound().build();
        }
        String html = TEST_PAGE.getContentAsString(StandardCharsets.UTF_8)
                .replace("__KAKAO_CLIENT_ID_BASE64__", encode(clientId))
                .replace("__KAKAO_REDIRECT_URI_BASE64__", encode(redirectUri));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
