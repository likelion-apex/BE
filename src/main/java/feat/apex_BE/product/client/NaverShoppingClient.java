package feat.apex_BE.product.client;

import feat.apex_BE.global.exception.CustomException;
import feat.apex_BE.global.exception.ErrorCode;
import feat.apex_BE.product.dto.response.NaverShoppingSearchResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

/**
 * Searches product listings through Naver's "Shopping" Search Open API and
 * returns the single most relevant result for a given keyword.
 *
 * <p>Unlike Coupang's Open API, this requires no HMAC request signing -
 * authentication is a pair of static HTTP headers issued instantly (no
 * sales-volume approval gate) from https://developers.naver.com.
 */
@Component
public class NaverShoppingClient {

    private static final int DISPLAY_COUNT = 1;
    private static final String SORT_BY_RELEVANCE = "sim";
    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String searchUri;

    public NaverShoppingClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.shopping.client-id}") String clientId,
            @Value("${naver.shopping.client-secret}") String clientSecret,
            @Value("${naver.shopping.search-uri}") String searchUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.searchUri = searchUri;
    }

    public Optional<NaverShoppingSearchResponse.Item> searchTopProduct(String keyword) {
        String query = "query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&display=" + DISPLAY_COUNT
                + "&sort=" + SORT_BY_RELEVANCE;
        URI uri = URI.create(searchUri + "?" + query);

        try {
            NaverShoppingSearchResponse response = restClient.get()
                    .uri(uri)
                    .header(CLIENT_ID_HEADER, clientId)
                    .header(CLIENT_SECRET_HEADER, clientSecret)
                    .retrieve()
                    .body(NaverShoppingSearchResponse.class);

            List<NaverShoppingSearchResponse.Item> items = response == null ? null : response.items();
            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(sanitizeTitle(items.get(0)));
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.NAVER_SEARCH_FAILED, e.getMessage());
        }
    }

    private NaverShoppingSearchResponse.Item sanitizeTitle(NaverShoppingSearchResponse.Item item) {
        String plainTitle = HtmlUtils.htmlUnescape(item.title().replaceAll("<[^>]*>", ""));
        return new NaverShoppingSearchResponse.Item(
                plainTitle,
                item.link(),
                item.image(),
                item.lprice(),
                item.hprice(),
                item.mallName(),
                item.productId(),
                item.brand(),
                item.maker()
        );
    }
}
