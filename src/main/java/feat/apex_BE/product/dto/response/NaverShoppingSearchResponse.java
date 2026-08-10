package feat.apex_BE.product.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response payload of Naver's "Shopping" Search Open API.
 * https://developers.naver.com/docs/serviceapi/search/shopping/shopping.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverShoppingSearchResponse(
        @JsonProperty("total") Long total,
        @JsonProperty("items") List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("title") String title,
            @JsonProperty("link") String link,
            @JsonProperty("image") String image,
            @JsonProperty("lprice") String lprice,
            @JsonProperty("hprice") String hprice,
            @JsonProperty("mallName") String mallName,
            @JsonProperty("productId") String productId,
            @JsonProperty("brand") String brand,
            @JsonProperty("maker") String maker
    ) {
    }
}
