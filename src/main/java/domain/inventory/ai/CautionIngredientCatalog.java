package domain.inventory.ai;

import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 20가지 주의성분 / 알레르기 유발 의심성분 참조 리스트. AI가 반환한 성분명이 이 리스트의
 * 어느 항목과도 (공백 무시, 양방향 부분일치로) 매칭되면 해당 성분을 주의/알레르기 성분으로 카운트한다.
 * 병기된 별칭(예: "옥시벤존"/"벤조페논-3")은 별도 항목으로 나누어 등록했다.
 */
@Component
public class CautionIngredientCatalog {

    private static final Set<String> CAUTION_20 = Set.of(
            "디부틸히드록시톨루엔",
            "옥시벤존",
            "벤조페논-3",
            "합성향료",
            "이소프로필 알코올",
            "파라벤",
            "설페이트",
            "폴리에틸렌글리콜",
            "트리에탄올아민",
            "이소프로필 메틸페놀",
            "사이클로 펜타실록산",
            "트리클로산",
            "미네랄오일",
            "페녹시에탄올",
            "티몰",
            "소르빅애씨드",
            "트리이소프로판올아민",
            "파라핀",
            "이미디아 졸리디닐우레아",
            "부틸 하이드록시 아니솔",
            "합성착색료"
    );

    private static final Set<String> ALLERGENS = Set.of(
            "아밀신남알",
            "아밀신나밀알코올",
            "아니스알코올",
            "벤질알코올",
            "벤질신나메이트",
            "벤질살리실레이트",
            "부틸페닐메틸프로피오날",
            "신남알",
            "신나밀알코올",
            "시트랄",
            "시트로넬올",
            "쿠마린",
            "제라니올",
            "헥실신남알",
            "하이드록시시트로넬알",
            "하이드록시이소헥실3-사이클로헥센카르복스알데히드",
            "이소유제놀",
            "리모넨",
            "리날룰",
            "메칠2-옥시노에이트",
            "오크모스추출물",
            "트리모스추출물",
            "파네솔",
            "유제놀",
            "아니스에탄올"
    );

    public int countCaution20(Collection<String> ingredientNames) {
        return countMatches(ingredientNames, CAUTION_20);
    }

    public int countAllergens(Collection<String> ingredientNames) {
        return countMatches(ingredientNames, ALLERGENS);
    }

    private int countMatches(Collection<String> ingredientNames, Set<String> masterList) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String name : ingredientNames) {
            if (matchesAny(name, masterList)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesAny(String ingredientName, Set<String> masterList) {
        String normalizedCandidate = normalize(ingredientName);
        if (normalizedCandidate.isEmpty()) {
            return false;
        }
        for (String entry : masterList) {
            String normalizedEntry = normalize(entry);
            if (normalizedCandidate.contains(normalizedEntry) || normalizedEntry.contains(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
