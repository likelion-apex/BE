package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import domain.cosmetic.client.OpenAiIngredientClient;
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 OpenAI Chat Completions 응답의 usage.prompt_tokens를 확인한다.
 * RUN_INVENTORY_TOKEN_LIVE_TEST=true 이고 OPENAI_API_KEY(.env 포함)가 있을 때만 실행한다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_INVENTORY_TOKEN_LIVE_TEST", matches = "true")
class InventoryOpenAiTokenUsageLiveTest {

    private static final int APP_INPUT_BUDGET = 600;
    private static final int APP_OUTPUT_BUDGET = 400;
    private static final int MODEL_CONTEXT_WINDOW = 128_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void reportsActualPromptTokensForInventoryPrompts() throws Exception {
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("test-") && !apiKey.startsWith("local-"),
                "OPENAI_API_KEY가 없어 실요청을 건너뜁니다.");

        List<String> typicalIngredients = List.of(
                "정제수", "부틸렌글라이콜", "글리세린", "나이아신아마이드", "판테놀",
                "히알루론산", "카프릴릴글라이콜", "에틸헥실글리세린", "1,2-헥산다이올", "토코페롤");
        String ingredientUser = "제품명: 라운드랩 1025 독도 토너";
        String purposeUser = "성분 목록: " + String.join(", ", typicalIngredients);
        String analysisUser = OpenAiPersonalizedAnalysisClient.buildUserContent(
                "라운드랩 1025 독도 토너", typicalIngredients, domain.member.SkinType.DRY, java.util.Set.of());

        StringBuilder report = new StringBuilder();
        report.append("model=gpt-4o-mini\n");
        report.append("app_input_budget=").append(APP_INPUT_BUDGET).append('\n');
        report.append("app_max_tokens=").append(APP_OUTPUT_BUDGET).append('\n');
        report.append("model_context_window=").append(MODEL_CONTEXT_WINDOW).append('\n');

        appendUsage(report, "ingredient",
                OpenAiIngredientClient.SYSTEM_PROMPT, ingredientUser, apiKey);
        appendUsage(report, "purpose",
                OpenAiIngredientClient.PURPOSE_SYSTEM_PROMPT, purposeUser, apiKey);
        appendUsage(report, "personalized",
                OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, analysisUser, apiKey);

        Path output = Path.of("build", "tmp", "openai-token-usage.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.print(report);

        assertThat(report).contains("prompt_tokens=");
    }

    private static void appendUsage(
            StringBuilder report, String name, String systemPrompt, String userPrompt, String apiKey)
            throws Exception {
        int estimated = InventoryAiTokenEstimator.estimate(systemPrompt, userPrompt);
        JsonNode response = complete(systemPrompt, userPrompt, apiKey);
        JsonNode usage = response.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(-1);
        int completionTokens = usage.path("completion_tokens").asInt(-1);
        int totalTokens = usage.path("total_tokens").asInt(-1);
        report.append('\n').append(name).append('\n');
        report.append("  estimated_input=").append(estimated).append('\n');
        report.append("  prompt_tokens=").append(promptTokens).append('\n');
        report.append("  completion_tokens=").append(completionTokens).append('\n');
        report.append("  total_tokens=").append(totalTokens).append('\n');
        report.append("  remaining_vs_app_budget=").append(APP_INPUT_BUDGET - promptTokens).append('\n');
        report.append("  remaining_vs_model_context=").append(MODEL_CONTEXT_WINDOW - totalTokens).append('\n');
        report.append("  over_app_budget=").append(promptTokens > APP_INPUT_BUDGET).append('\n');
    }

    private static JsonNode complete(String systemPrompt, String userPrompt, String apiKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0);
        body.put("max_tokens", APP_OUTPUT_BUDGET);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        String requestJson = OBJECT_MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("OpenAI HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return InventoryAiJsonSupport.readObject(OBJECT_MAPPER, response.body());
    }

    private static String resolveApiKey() throws Exception {
        String fromEnv = System.getenv("OPENAI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return null;
        }
        for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("OPENAI_API_KEY=")) {
                String value = trimmed.substring("OPENAI_API_KEY=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
