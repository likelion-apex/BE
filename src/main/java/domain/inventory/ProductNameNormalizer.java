package domain.inventory;

import java.text.Normalizer;

/**
 * 표시용 제품명에서 비교 키를 만든다. 숫자·공백·기호를 버리고 한글 음절만 남기므로
 * {@code 바닥 토너}와 {@code 바닥 토너 01}은 같은 상품으로 본다.
 */
public final class ProductNameNormalizer {

    private ProductNameNormalizer() {
    }

    public static String canonicalKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String nfc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        StringBuilder hangul = new StringBuilder();
        StringBuilder alnum = new StringBuilder();
        for (int i = 0; i < nfc.length(); ) {
            int codePoint = nfc.codePointAt(i);
            if (codePoint >= 0xAC00 && codePoint <= 0xD7A3) {
                hangul.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                alnum.appendCodePoint(Character.toLowerCase(codePoint));
            }
            i += Character.charCount(codePoint);
        }
        if (!hangul.isEmpty()) {
            return hangul.toString();
        }
        if (!alnum.isEmpty()) {
            return alnum.toString();
        }
        return nfc.replaceAll("\\s+", "");
    }
}
