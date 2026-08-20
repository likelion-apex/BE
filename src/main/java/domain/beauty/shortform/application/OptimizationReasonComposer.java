package domain.beauty.shortform.application;

import domain.beauty.shortform.application.ShortformAnalysisStateService.AnalysisProfile;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class OptimizationReasonComposer {

    private static final int MAX_REASON_LENGTH = 240;
    private static final List<String> GENERIC_OR_INTERNAL = List.of(
            "대체 가능합니다",
            "같은 카테고리",
            "카테고리가 달라",
            "영상 속 제품을 유지",
            "인벤토리에서 같은",
            "추천된 인벤토리",
            "서버 보정"
    );

    private final KoreanUserCopyNormalizer koreanCopy;

    public OptimizationReasonComposer(KoreanUserCopyNormalizer koreanCopy) {
        this.koreanCopy = koreanCopy;
    }

    public String forNewAnalysis(
            JobContext profile,
            MatchedVideoStep source,
            StepResult analysisStep,
            InventoryFact replacement,
            InventoryProductEvidence replacementEvidence,
            String aiReason
    ) {
        ProductFacts video = new ProductFacts(
                displayName(source.displayProductName(), source.source().productName(), source.source().category()),
                source.source().purpose(),
                safe(analysisStep == null ? null : analysisStep.keyBenefits()),
                safe(source.enrichment().ingredients()).stream().map(IngredientFact::from).toList()
        );
        ProductFacts owned = replacement == null ? null : new ProductFacts(
                replacement.productName(),
                null,
                List.of(),
                evidenceIngredients(replacementEvidence)
        );
        return resolve(profile.skinType(), profile.skinConcerns(), video, owned, aiReason);
    }

    public String forStoredAnalysis(
            AnalysisProfile profile,
            StepResult source,
            OptimizedStep optimized,
            InventoryProductEvidence replacementEvidence,
            String aiReason
    ) {
        ProductFacts video = new ProductFacts(
                displayName(source.displayProductName(), source.productName(), source.category()),
                firstText(source.matchSummary(), source.evidenceSummary(), source.category()),
                safe(source.keyBenefits()),
                safe(source.ingredients()).stream().map(IngredientFact::from).toList()
        );
        ProductFacts owned = optimized.status() != OptimizationStatus.REPLACED ? null : new ProductFacts(
                optimized.productName(),
                null,
                List.of(),
                evidenceIngredients(replacementEvidence)
        );
        return resolve(profile.skinType(), profile.skinConcerns(), video, owned, aiReason);
    }

    private String resolve(
            String skinType,
            List<String> skinConcerns,
            ProductFacts video,
            ProductFacts owned,
            String aiReason
    ) {
        String candidate = normalize(aiReason);
        if (isGrounded(candidate, video, owned, skinType, skinConcerns)) {
            return candidate;
        }
        return owned == null
                ? missingReason(video, skinType, skinConcerns)
                : replacementReason(video, owned, skinType, skinConcerns);
    }

    private boolean isGrounded(
            String reason,
            ProductFacts video,
            ProductFacts owned,
            String skinType,
            List<String> skinConcerns
    ) {
        if (reason == null || !koreanCopy.isAcceptable(reason) || reason.contains("님")
                || GENERIC_OR_INTERNAL.stream().anyMatch(reason::contains)) {
            return false;
        }
        if (owned != null && !containsProductReference(reason, owned.name())) {
            return false;
        }
        Set<String> facts = facts(video, owned, skinType, skinConcerns);
        boolean concrete = facts.stream()
                .filter(value -> value.length() >= 2)
                .anyMatch(reason::contains);
        if (!concrete) {
            return false;
        }
        if (reason.contains("성분")) {
            List<String> supportedIngredientPhrases = new ArrayList<>();
            Stream.concat(
                            video.ingredients().stream(),
                            owned == null ? Stream.empty() : owned.ingredients().stream())
                    .forEach(item -> {
                        supportedIngredientPhrases.add(item.name());
                        supportedIngredientPhrases.addAll(item.purposes());
                        supportedIngredientPhrases.addAll(item.benefits());
                    });
            supportedIngredientPhrases.addAll(safe(video.benefits()));
            supportedIngredientPhrases.add(video.purpose());
            if (!allIngredientMentionsGrounded(reason, supportedIngredientPhrases)) {
                return false;
            }
        }
        return true;
    }

    private boolean allIngredientMentionsGrounded(String reason, List<String> supportedPhrases) {
        int from = 0;
        while (true) {
            int marker = reason.indexOf("성분", from);
            if (marker < 0) {
                return true;
            }
            String preceding = reason.substring(Math.max(0, marker - 30), marker);
            boolean supported = supportedPhrases.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .anyMatch(preceding::contains);
            if (!supported) {
                return false;
            }
            from = marker + 2;
        }
    }

    private String replacementReason(
            ProductFacts video,
            ProductFacts owned,
            String skinType,
            List<String> concerns
    ) {
        String sharedIngredient = video.ingredients().stream()
                .map(IngredientFact::name)
                .filter(koreanCopy::isAcceptable)
                .filter(name -> name != null && owned.ingredients().stream()
                        .map(IngredientFact::name)
                        .anyMatch(name::equalsIgnoreCase))
                .findFirst()
                .orElse(null);
        String benefit = firstText(
                commonFact(video.ingredients(), owned.ingredients()),
                firstKorean(video.benefits()),
                koreanCopy.isAcceptable(video.purpose()) ? video.purpose() : null,
                "같은 단계의 피부 관리"
        );
        String profile = profilePhrase(skinType, concerns);
        if (sharedIngredient != null) {
            return limit("영상 속 %s와 보유하신 %s에 모두 %s이 확인되어 %s 목적을 이어갈 수 있어요. %s추가 구매 없이 보유 제품을 사용해 보세요."
                    .formatted(video.name(), owned.name(), sharedIngredient, benefit, profile));
        }
        return limit("보유하신 %s은 영상 속 %s와 같은 단계에서 %s 역할을 이어갈 수 있어요. %s추가 구매 없이 보유 제품으로 관리해 보세요."
                .formatted(owned.name(), video.name(), benefit, profile));
    }

    private String missingReason(ProductFacts video, String skinType, List<String> concerns) {
        IngredientFact ingredient = video.ingredients().stream()
                .filter(item -> koreanCopy.isAcceptable(item.name()))
                .findFirst()
                .orElse(null);
        String profile = profilePhrase(skinType, concerns);
        if (ingredient != null) {
            String benefit = firstText(
                    firstKorean(ingredient.benefits()),
                    firstKorean(ingredient.purposes()),
                    koreanCopy.isAcceptable(video.purpose()) ? video.purpose() : null,
                    "피부 관리");
            return limit("영상 속 %s의 %s은 %s 역할을 해요. %s이 역할을 대신할 확인된 보유 제품이 없어 영상 속 제품을 사용해 주세요."
                    .formatted(video.name(), ingredient.name(), benefit, profile));
        }
        String purpose = firstText(
                firstKorean(video.benefits()),
                koreanCopy.isAcceptable(video.purpose()) ? video.purpose() : null,
                "해당 단계의 피부 관리");
        return limit("영상 속 %s은 %s을 위한 제품이에요. %s같은 역할을 맡길 확인된 보유 제품이 없어 영상 속 제품을 사용해 주세요."
                .formatted(video.name(), purpose, profile));
    }

    private String profilePhrase(String skinType, List<String> concerns) {
        String concern = first(safe(concerns));
        if (concern != null) {
            return concern + " 고민을 고려하면 ";
        }
        if (skinType != null && !skinType.isBlank()) {
            return skinType + " 피부를 고려하면 ";
        }
        return "";
    }

    private Set<String> facts(ProductFacts video, ProductFacts owned, String skinType, List<String> concerns) {
        Set<String> facts = new LinkedHashSet<>();
        add(facts, video.name());
        add(facts, video.purpose());
        safe(video.benefits()).forEach(value -> add(facts, value));
        video.ingredients().forEach(ingredient -> {
            add(facts, ingredient.name());
            ingredient.purposes().forEach(value -> add(facts, value));
            ingredient.benefits().forEach(value -> add(facts, value));
        });
        if (owned != null) {
            add(facts, owned.name());
            owned.ingredients().forEach(ingredient -> {
                add(facts, ingredient.name());
                ingredient.purposes().forEach(value -> add(facts, value));
                ingredient.benefits().forEach(value -> add(facts, value));
            });
        }
        add(facts, skinType);
        safe(concerns).forEach(value -> add(facts, value));
        return facts;
    }

    private boolean containsProductReference(String reason, String productName) {
        if (productName == null || productName.isBlank()) {
            return false;
        }
        if (reason.contains(productName)) {
            return true;
        }
        return Stream.of(productName.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .anyMatch(reason::contains);
    }

    private String commonFact(List<IngredientFact> left, List<IngredientFact> right) {
        Set<String> rightFacts = new LinkedHashSet<>();
        right.forEach(item -> {
            rightFacts.addAll(item.purposes());
            rightFacts.addAll(item.benefits());
        });
        return left.stream()
                .flatMap(item -> Stream.concat(item.benefits().stream(), item.purposes().stream()))
                .filter(koreanCopy::isAcceptable)
                .filter(rightFacts::contains)
                .findFirst()
                .orElse(null);
    }

    private List<IngredientFact> evidenceIngredients(InventoryProductEvidence evidence) {
        if (evidence == null || !evidence.isAvailable()) {
            return List.of();
        }
        return safe(evidence.ingredients()).stream().map(IngredientFact::from).toList();
    }

    private void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return limit(value.replaceAll("\\s+", " ").trim());
    }

    private String limit(String value) {
        return value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }

    private String displayName(String... values) {
        return firstText(values);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "제품";
    }

    private String first(List<String> values) {
        return values == null ? null : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private String firstKorean(List<String> values) {
        return safe(values).stream()
                .filter(koreanCopy::isAcceptable)
                .findFirst()
                .orElse(null);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ProductFacts(
            String name,
            String purpose,
            List<String> benefits,
            List<IngredientFact> ingredients
    ) {
    }

    private record IngredientFact(String name, List<String> purposes, List<String> benefits) {
        static IngredientFact from(ProductEnrichmentResult.Ingredient ingredient) {
            return new IngredientFact(
                    ingredient.name(),
                    ingredient.purposes() == null ? List.of() : ingredient.purposes(),
                    ingredient.skinBenefits() == null ? List.of() : ingredient.skinBenefits());
        }

        static IngredientFact from(IngredientDetail ingredient) {
            return new IngredientFact(
                    ingredient.name(),
                    ingredient.purposes() == null ? List.of() : ingredient.purposes(),
                    ingredient.skinBenefits() == null ? List.of() : ingredient.skinBenefits());
        }
    }
}
