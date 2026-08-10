package domain.cosmetic.client;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 식품의약품안전처_화장품 원료성분정보 API 클라이언트.
 * INGR_KOR_NAME 파라미터로 부분일치 검색이 가능하므로, 정확히 일치하는 항목을 우선 채택한다.
 */
@Slf4j
@Component
public class CsmtcsIngdCpntClient {

    private final RestClient restClient;
    private final String serviceKey;

    public CsmtcsIngdCpntClient(
            @Value("${mfds.service-key}") String serviceKey,
            @Value("${mfds.ingredient-info-url}") String baseUrl) {
        this.serviceKey = serviceKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public IngredientInfo findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 20)
                            .queryParam("type", "json")
                            .queryParam("INGR_KOR_NAME", name)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            List<JsonNode> items = MfdsResponseParser.extractItems(response);
            if (items.isEmpty()) {
                return null;
            }
            JsonNode matched = items.stream()
                    .filter(item -> name.equals(MfdsResponseParser.textOrNull(item, "INGR_KOR_NAME")))
                    .findFirst()
                    .orElse(items.get(0));

            return new IngredientInfo(
                    MfdsResponseParser.textOrNull(matched, "INGR_KOR_NAME"),
                    MfdsResponseParser.textOrNull(matched, "INGR_ENG_NAME"),
                    MfdsResponseParser.textOrNull(matched, "CAS_NO"),
                    MfdsResponseParser.textOrNull(matched, "ORIGIN_MAJOR_KOR_NAME"),
                    MfdsResponseParser.textOrNull(matched, "INGR_SYNONYM")
            );
        } catch (RestClientException e) {
            log.warn("화장품 원료성분정보 조회 실패: name={}, message={}", name, e.getMessage());
            return null;
        }
    }
}
