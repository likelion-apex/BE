package domain.ingredient.service;

import domain.ingredient.domain.Ingredient;
import domain.ingredient.domain.ProductIngredient;
import domain.ingredient.domain.RiskLevel;
import domain.ingredient.dto.response.AnalysisReason;
import domain.ingredient.dto.response.SkinAnalysisResponse;
import domain.ingredient.dto.response.SkinAnalysisResponse.AiAnalysis;
import domain.ingredient.dto.response.SkinAnalysisResponse.IngredientDetail;
import domain.ingredient.dto.response.SkinAnalysisResponse.IngredientProfile;
import domain.ingredient.dto.response.SkinAnalysisResponse.RiskDistribution;
import domain.ingredient.repository.ProductIngredientRepository;
import domain.inventory.Product;
import domain.inventory.service.ProductService;
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 피부적합도 분석(4.4). ChatGPT 맞춤 분석을 우선 사용해 matchScore/aiAnalysis를 산출하고,
 * AI 호출이 실패(null)하면 저장된 성분의 EWG 등급 기반 규칙으로 matchScore를 대체 산출한다.
 * safetyEvaluation 한 줄 요약은 AI 성패와 무관하게 항상 EWG 등급 분포 기반 규칙으로 생성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkinAnalysisService {

    private static final int DEFAULT_FALLBACK_SCORE = 70;
    private static final int FALLBACK_SCORE_INTERCEPT = 110;
    private static final int FALLBACK_SCORE_SLOPE = 10;
    private static final String AI_DISCLAIMER = "본 분석은 AI가 제공하는 참고 정보이며 의학적 진단을 대체하지 않습니다.";
    private static final String FALLBACK_DISCLAIMER = "AI 분석에 실패하여 EWG 등급 기반 규칙으로 대체 산출된 결과입니다.";

    private final ProductService productService;
    private final MemberRepository memberRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final OpenAiPersonalizedAnalysisClient personalizedAnalysisClient;

    public SkinAnalysisResponse analyze(Long memberId, Long productId) {
        Product product = productService.getById(productId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProduct_IdOrderByRankAsc(productId);

        IngredientProfile ingredientProfile = buildIngredientProfile(productIngredients);
        String safetyEvaluation = buildSafetyEvaluation(ingredientProfile);

        PersonalizedAnalysisResult aiResult = personalizedAnalysisClient.analyze(
                product.getName(),
                productIngredients.stream().map(pi -> pi.getIngredient().getName()).toList(),
                member.getSkinType(),
                member.getSkinConcerns());

        int matchScore;
        AiAnalysis aiAnalysis;
        if (aiResult != null) {
            matchScore = aiResult.score();
            List<AnalysisReason> reasons = aiResult.keywords().stream()
                    .map(keyword -> new AnalysisReason(keyword.keyword(), keyword.reason()))
                    .toList();
            aiAnalysis = new AiAnalysis(reasons, AI_DISCLAIMER);
        } else {
            matchScore = fallbackScore(productIngredients);
            aiAnalysis = new AiAnalysis(List.of(), FALLBACK_DISCLAIMER);
        }

        return new SkinAnalysisResponse(
                productId, product.getName(), matchScore, safetyEvaluation, aiAnalysis, ingredientProfile, LocalDateTime.now());
    }

    private IngredientProfile buildIngredientProfile(List<ProductIngredient> productIngredients) {
        List<IngredientDetail> details = productIngredients.stream()
                .map(productIngredient -> {
                    Ingredient ingredient = productIngredient.getIngredient();
                    RiskLevel riskLevel = RiskLevel.fromEwgGrade(ingredient.getEwgGrade());
                    int rank = productIngredient.getRank() != null ? productIngredient.getRank() : 0;
                    return new IngredientDetail(rank, ingredient.getName(), riskLevel, List.of(), List.of());
                })
                .toList();

        int low = (int) details.stream().filter(detail -> detail.riskLevel() == RiskLevel.LOW).count();
        int mid = (int) details.stream().filter(detail -> detail.riskLevel() == RiskLevel.MID).count();
        int high = (int) details.stream().filter(detail -> detail.riskLevel() == RiskLevel.HIGH).count();

        return new IngredientProfile(details.size(), mid + high, 0, new RiskDistribution(low, mid, high), details);
    }

    private String buildSafetyEvaluation(IngredientProfile profile) {
        if (profile.totalCount() == 0) {
            return "성분 정보가 없어 안전성을 평가할 수 없어요.";
        }
        RiskDistribution distribution = profile.riskDistribution();
        if (distribution.high() > 0) {
            return "위험도가 높은 성분이 %d개 포함되어 있어 민감성 피부는 사용에 주의가 필요해요.".formatted(distribution.high());
        }
        if (distribution.mid() > 0) {
            return "주의가 필요한 성분이 %d개 있지만 전반적으로 순한 편이에요.".formatted(distribution.mid());
        }
        return "위험도가 낮은 성분으로 구성되어 있어 안심하고 사용할 수 있어요.";
    }

    private int fallbackScore(List<ProductIngredient> productIngredients) {
        List<Integer> grades = productIngredients.stream()
                .map(productIngredient -> productIngredient.getIngredient().getEwgGrade())
                .filter(grade -> grade != null)
                .toList();
        if (grades.isEmpty()) {
            return DEFAULT_FALLBACK_SCORE;
        }
        double averageGrade = grades.stream().mapToInt(Integer::intValue).average().orElseThrow();
        int score = (int) Math.round(FALLBACK_SCORE_INTERCEPT - FALLBACK_SCORE_SLOPE * averageGrade);
        return Math.max(0, Math.min(100, score));
    }
}