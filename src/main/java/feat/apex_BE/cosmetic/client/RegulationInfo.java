package feat.apex_BE.cosmetic.client;

import java.util.List;

/**
 * 화장품 규제정보 API 응답을 담는 내부 전용 값 객체.
 */
public record RegulationInfo(
        String standardName,
        String englishName,
        List<String> prohibitedCountries,
        List<String> restrictedCountries
) {
}
