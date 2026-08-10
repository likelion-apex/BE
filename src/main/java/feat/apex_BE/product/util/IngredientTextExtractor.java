package feat.apex_BE.product.util;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Parses the ingredient ("전성분") block out of raw OCR text extracted from
 * a cosmetic product's detail-page images.
 */
@Component
public class IngredientTextExtractor {

    private static final List<String> INGREDIENT_KEYWORDS = List.of("전성분", "성분");

    private static final List<String> STOP_KEYWORDS = List.of(
            "사용법", "사용방법", "주의사항", "제조판매업자", "제조업자",
            "품질보증기준", "사용기한", "보관방법", "원산지", "고객센터"
    );

    private static final int MAX_LENGTH = 1500;

    /**
     * @param ocrTexts OCR text extracted from each detail-page image, in page order
     * @return the ingredient text block, if any of the images contained one
     */
    public Optional<String> extract(List<String> ocrTexts) {
        for (String text : ocrTexts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Optional<String> extracted = extractFromSingleText(text);
            if (extracted.isPresent()) {
                return extracted;
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFromSingleText(String text) {
        for (String keyword : INGREDIENT_KEYWORDS) {
            int index = text.indexOf(keyword);
            if (index < 0) {
                continue;
            }

            String afterKeyword = text.substring(index + keyword.length())
                    .replaceFirst("^[\\s:：]+", "");
            String cleaned = cleanWhitespace(cutAtStopKeyword(afterKeyword));
            if (!cleaned.isBlank()) {
                return Optional.of(cleaned);
            }
        }
        return Optional.empty();
    }

    private String cutAtStopKeyword(String text) {
        int earliestStopIndex = text.length();
        for (String stopKeyword : STOP_KEYWORDS) {
            int stopIndex = text.indexOf(stopKeyword);
            if (stopIndex >= 0 && stopIndex < earliestStopIndex) {
                earliestStopIndex = stopIndex;
            }
        }
        String cut = text.substring(0, earliestStopIndex);
        return cut.length() > MAX_LENGTH ? cut.substring(0, MAX_LENGTH) : cut;
    }

    private String cleanWhitespace(String text) {
        return text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
