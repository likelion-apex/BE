package global.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 정적 자산(카테고리 이미지 등)은 DB/코드에 호스트 없는 상대경로(예: {@code /images/categories/bam.png})로
 * 저장되어 있다. 프론트엔드와 백엔드가 서로 다른 도메인에 배포되어 있어, 상대경로를 그대로 응답하면
 * 브라우저가 프론트엔드 도메인을 기준으로 잘못 해석해 이미지가 뜨지 않는다.
 * 이 컴포넌트는 API 응답을 만들 때 상대경로 앞에 백엔드 자체 도메인({@code app.public-base-url})을
 * 붙여 절대 URL로 바꿔준다. 기준 도메인이 설정되어 있지 않으면(로컬/테스트 기본값) 입력을 그대로 반환한다.
 */
@Component
public class PublicUrlResolver {

    private final String baseUrl;

    public PublicUrlResolver(@Value("${app.public-base-url:}") String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.strip();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public String resolve(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (baseUrl.isEmpty()) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }
}