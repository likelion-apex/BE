package domain.beauty.shortform.application;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProductCapacityNormalizer {

    private static final Pattern CAPACITY_PATTERN = Pattern.compile(
            "(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(ml|㎖|l|ℓ|g|kg|㎏)(?![a-z])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public String normalize(String... candidates) {
        Set<String> capacities = new LinkedHashSet<>();
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Matcher matcher = CAPACITY_PATTERN.matcher(candidate);
            while (matcher.find()) {
                capacities.add(matcher.group(1) + normalizeUnit(matcher.group(2)));
            }
        }
        return capacities.size() == 1 ? capacities.iterator().next() : null;
    }

    private String normalizeUnit(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "ml", "㎖" -> "ml";
            case "l", "ℓ" -> "L";
            case "kg", "㎏" -> "kg";
            default -> "g";
        };
    }
}
