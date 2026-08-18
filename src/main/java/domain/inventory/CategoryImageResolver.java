package domain.inventory;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * ProductCategory에 대응하는 정적 카테고리 이미지의 상대 경로를 반환한다.
 * 이미지 파일은 {@code src/main/resources/static/images/categories/}에 카테고리 이름을
 * 소문자로 변환한 파일명(예: SKIN_TONER -> skin_toner.png)으로 배치되어 있다.
 */
@Component
public class CategoryImageResolver {

    private static final String BASE_PATH = "/images/categories/";

    /** ETC는 매칭되는 이미지 자산이 없어 null을 반환한다. */
    public String resolve(ProductCategory category) {
        if (category == null || category == ProductCategory.ETC) {
            return null;
        }
        return BASE_PATH + category.name().toLowerCase(Locale.ROOT) + ".png";
    }
}
