package domain.cosmetic.client;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 식품의약품안전처_화장품 규제정보 API 클라이언트.
 * 성분명으로 서버 필터링이 되지 않으므로(테스트로 확인됨), 전체 데이터를 페이지네이션으로 모두 가져온다.
 * 호출 측(RegulationInfoCache)에서 전량 조회 후 로컬에 캐싱해 이름으로 조회한다.
 */
@Slf4j
@Component
public class CsmtcsReglMaterialClient {

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final String serviceKey;

    public CsmtcsReglMaterialClient(
            @Value("${mfds.service-key}") String serviceKey,
            @Value("${mfds.regulation-info-url}") String baseUrl) {
        this.serviceKey = serviceKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<RegulationInfo> fetchAll() {
        List<RegulationInfo> result = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE;

        while ((pageNo - 1) * PAGE_SIZE < totalCount) {
            int currentPage = pageNo;
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", currentPage)
                            .queryParam("numOfRows", PAGE_SIZE)
                            .queryParam("type", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            totalCount = MfdsResponseParser.extractTotalCount(response);
            List<JsonNode> items = MfdsResponseParser.extractItems(response);
            if (items.isEmpty()) {
                break;
            }
            for (JsonNode item : items) {
                result.add(new RegulationInfo(
                        MfdsResponseParser.textOrNull(item, "INGR_STD_NAME"),
                        MfdsResponseParser.textOrNull(item, "INGR_ENG_NAME"),
                        MfdsResponseParser.splitCountries(MfdsResponseParser.textOrNull(item, "PROH_NATIONAL")),
                        MfdsResponseParser.splitCountries(MfdsResponseParser.textOrNull(item, "LIMIT_NATIONAL"))
                ));
            }
            pageNo++;
        }
        return result;
    }
}
