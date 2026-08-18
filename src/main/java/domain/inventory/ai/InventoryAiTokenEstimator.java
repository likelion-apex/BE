package domain.inventory.ai;

/**
 * 인벤토리 프롬프트의 대략적인 토큰 수. 한글은 글자 2개당 약 1토큰으로 본다.
 */
public final class InventoryAiTokenEstimator {

    private InventoryAiTokenEstimator() {
    }

    public static int estimate(String... texts) {
        int chars = 0;
        if (texts != null) {
            for (String text : texts) {
                if (text != null) {
                    chars += text.length();
                }
            }
        }
        return Math.max(1, (chars + 1) / 2);
    }
}
