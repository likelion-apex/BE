package config;

import java.nio.charset.StandardCharsets;

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

    public KakaoLoginTestPageController(
            @Value("${features.kakao-login-test.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    @GetMapping(value = {"/kakao-login-test", "/kakao-login-test/"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> page() {
        if (!enabled) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(TEST_PAGE);
    }
}
