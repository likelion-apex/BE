package domain.beauty.shortform.application;

import domain.beauty.shortform.application.ShortformAnalysisStateService.AnalysisProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class AnalysisFingerprint {

    public String create(String videoId, AnalysisProfile profile) {
        String concerns = profile.skinConcerns().stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
        String inventory = profile.inventory().stream()
                .sorted((left, right) -> left.inventoryId().compareTo(right.inventoryId()))
                .map(item -> item.inventoryId() + ":" + item.productId() + ":" + item.category() + ":" + item.productName())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String source = String.join("#", videoId, profile.skinType(), concerns, inventory, "shortform-personalization-v1");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
