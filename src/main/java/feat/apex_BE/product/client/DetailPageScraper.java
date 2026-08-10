package feat.apex_BE.product.client;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches a product detail page (from any online mall, e.g. Naver
 * SmartStore or an independent shopping-mall site) and extracts the
 * description-area image URLs, where a cosmetic product's ingredient list
 * is usually shown as an image.
 *
 * <p><b>Known limitation:</b> because Naver's Shopping search results link
 * to a wide variety of malls, each with its own HTML structure, description
 * images are frequently injected client-side via JavaScript after page
 * load, and some sites front their pages with bot-protection that rejects
 * plain HTTP clients outright. A static parser such as Jsoup may therefore
 * return an empty result for some/many products. This is treated as a soft
 * failure: callers should fall back to {@code ingredients = null} rather
 * than failing the whole request (see {@code ProductSearchService}). If
 * this turns out to be the common case in practice, a headless-browser
 * renderer (e.g. Playwright) should be evaluated as a follow-up.
 */
@Component
public class DetailPageScraper {

    private static final Logger log = LoggerFactory.getLogger(DetailPageScraper.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_IMAGES = 15;

    /** Candidate containers used by common Korean shopping-mall platforms for the product description area. */
    private static final String[] DETAIL_CONTAINER_SELECTORS = {
            "div.se-main-container",   // Naver SmartStore (Smart Editor)
            "div#prdDetail",           // Cafe24
            "div.detailArea",          // Cafe24 variants
            "div#tblDetail",           // Godomall
            "div#productDetail",
            "div.product-detail",
            "div.prod-description",
            "div.prod-detail-attribute",
    };

    /** Filename fragments that indicate a non-description image (logo, icon, banner, etc.). */
    private static final String[] IMAGE_EXCLUDE_HINTS = {
            "logo", "icon", "btn_", "banner", "sprite",
    };

    public List<String> extractDetailImageUrls(String productUrl) {
        try {
            Document document = Jsoup.connect(productUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout((int) TIMEOUT.toMillis())
                    .get();

            Set<String> imageUrls = new LinkedHashSet<>();
            for (String selector : DETAIL_CONTAINER_SELECTORS) {
                Elements containers = document.select(selector);
                if (!containers.isEmpty()) {
                    addImageUrls(containers.select("img"), imageUrls);
                }
                if (!imageUrls.isEmpty()) {
                    break;
                }
            }

            if (imageUrls.isEmpty()) {
                addImageUrls(document.select("img"), imageUrls);
            }

            return imageUrls.stream().limit(MAX_IMAGES).toList();
        } catch (IOException e) {
            log.warn("상품 상세페이지 스크래핑에 실패했습니다. url={}, reason={}", productUrl, e.getMessage());
            return List.of();
        }
    }

    private void addImageUrls(Elements imgElements, Set<String> target) {
        for (Element img : imgElements) {
            String src = img.hasAttr("data-src") ? img.attr("abs:data-src") : img.attr("abs:src");
            if (src.isBlank() || isExcluded(src)) {
                continue;
            }
            target.add(normalize(src));
        }
    }

    private boolean isExcluded(String src) {
        String lower = src.toLowerCase();
        for (String hint : IMAGE_EXCLUDE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String url) {
        return url.startsWith("//") ? "https:" + url : url;
    }
}
