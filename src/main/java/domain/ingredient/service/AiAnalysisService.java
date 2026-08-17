package domain.ingredient.service;

import domain.inventory.ai.IngredientAiClient;
import domain.ingredient.client.GradeAnalysisResult;
import domain.ingredient.client.OpenAiGradeAnalysisClient;
import domain.ingredient.dto.response.AiRoutineAnalysisResponse;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 루틴 분석(4.5). productId 없이 임의의 제품명만으로, 회원의 피부타입/피부고민을 반영한
 * 종합 등급(SAFE/MEH/GOOD/RISK)과 근거 코멘트를 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisService {

    private final MemberRepository memberRepository;
    private final IngredientAiClient ingredientAiClient;
    private final OpenAiGradeAnalysisClient gradeAnalysisClient;

    public AiRoutineAnalysisResponse analyze(Long memberId, String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "productName은 필수입니다.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String trimmedName = productName.trim();
        List<String> ingredientNames = ingredientAiClient.fetchIngredientNames(trimmedName);

        GradeAnalysisResult result = gradeAnalysisClient.analyze(
                trimmedName, ingredientNames, member.getSkinType(), member.getSkinConcerns());
        if (result == null) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        return new AiRoutineAnalysisResponse(trimmedName, result.grade(), result.comment());
    }
}
