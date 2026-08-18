package domain.beauty.shortform.client;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiModelRouter {

    private static final Pattern RETRY_SECONDS = Pattern.compile(
            "(?:retry in|retry after)\\s+(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY_FIELD = Pattern.compile(
            "\\\"retryDelay\\\"\\s*:\\s*\\\"(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);

    private final GeminiProperties geminiProperties;
    private final ShortformAiFallbackProperties routingProperties;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, ModelState> states = new ConcurrentHashMap<>();

    @Autowired
    public GeminiModelRouter(
            GeminiProperties geminiProperties,
            ShortformAiFallbackProperties routingProperties
    ) {
        this(geminiProperties, routingProperties, Clock.systemUTC(), Thread::sleep);
    }

    GeminiModelRouter(
            GeminiProperties geminiProperties,
            ShortformAiFallbackProperties routingProperties,
            Clock clock,
            Sleeper sleeper
    ) {
        this.geminiProperties = geminiProperties;
        this.routingProperties = routingProperties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public <T> T route(GeminiRouteProfile profile, String operation, CandidateCall<T> candidateCall) {
        List<String> models = models(profile);
        if (models.isEmpty()) {
            throw new GeminiModelRoutingException("Gemini 라우팅 모델이 설정되지 않았습니다.", null, true);
        }

        AttemptResult<T> first = attempt(models, operation, candidateCall);
        if (first.value() != null) {
            return first.value();
        }

        Duration delay = retryDelay(first.earliestRetryAt());
        if (delay != null) {
            log.warn("Gemini 숏폼 {} 전체 후보 실패로 한 번 지연 재시도합니다: delayMs={}",
                    operation, delay.toMillis());
            sleep(delay);
            AttemptResult<T> second = attempt(models, operation, candidateCall);
            if (second.value() != null) {
                return second.value();
            }
            throw exhausted(operation, second.lastFailure());
        }
        throw exhausted(operation, first.lastFailure());
    }

    private <T> AttemptResult<T> attempt(
            List<String> models,
            String operation,
            CandidateCall<T> candidateCall
    ) {
        Instant earliestRetryAt = null;
        Throwable lastFailure = null;
        for (String model : models) {
            ModelState state = states.computeIfAbsent(model, ignored -> new ModelState());
            Instant now = clock.instant();
            if (state.unsupported) {
                continue;
            }
            if (state.cooldownUntil.isAfter(now)) {
                earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                continue;
            }

            state.lock.lock();
            try {
                now = clock.instant();
                if (state.unsupported) {
                    continue;
                }
                if (state.cooldownUntil.isAfter(now)) {
                    earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                    continue;
                }
                try {
                    T value = candidateCall.call(model);
                    state.cooldownUntil = Instant.EPOCH;
                    log.info("Gemini 숏폼 {} 모델 라우팅 성공: model={}", operation, model);
                    return new AttemptResult<>(value, earliestRetryAt, lastFailure);
                } catch (RestClientResponseException exception) {
                    lastFailure = exception;
                    int status = exception.getStatusCode().value();
                    log.warn("Gemini 숏폼 {} 후보 HTTP 실패: status={}, model={}", operation, status, model);
                    if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                            || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                        throw new GeminiModelRoutingException(
                                "Gemini API 키 또는 프로젝트 권한을 확인해 주세요.", exception, true);
                    }
                    if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                        state.unsupported = true;
                        continue;
                    }
                    if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        state.cooldownUntil = now.plus(rateLimitDelay(exception));
                        earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                        continue;
                    }
                    if (exception.getStatusCode().is5xxServerError()) {
                        state.cooldownUntil = now.plus(transientDelay());
                        earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                    }
                } catch (ResourceAccessException exception) {
                    lastFailure = exception;
                    state.cooldownUntil = now.plus(transientDelay());
                    earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                    log.warn("Gemini 숏폼 {} 후보 연결 실패: model={}", operation, model);
                } catch (GeminiCandidateRejectedException exception) {
                    lastFailure = exception;
                    log.warn("Gemini 숏폼 {} 후보 응답 거부: model={}, reason={}",
                            operation, model, exception.getMessage());
                } catch (RestClientException exception) {
                    lastFailure = exception;
                    state.cooldownUntil = now.plus(transientDelay());
                    earliestRetryAt = earlier(earliestRetryAt, state.cooldownUntil);
                    log.warn("Gemini 숏폼 {} 후보 요청 실패: model={}", operation, model);
                }
            } finally {
                state.lock.unlock();
            }
        }
        return new AttemptResult<>(null, earliestRetryAt, lastFailure);
    }

    private List<String> models(GeminiRouteProfile profile) {
        List<String> configured = switch (profile) {
            case VIDEO -> routingProperties.getGeminiVideoModels();
            case TEXT -> routingProperties.getGeminiTextModels();
            case PRODUCT -> routingProperties.getGeminiProductModels();
        };
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (profile == GeminiRouteProfile.VIDEO) {
            addModel(models, geminiProperties.getModel());
        }
        if (configured != null) {
            configured.forEach(model -> addModel(models, model));
        }
        return new ArrayList<>(models);
    }

    private void addModel(LinkedHashSet<String> models, String model) {
        if (model != null && !model.isBlank()) {
            models.add(model.trim());
        }
    }

    private Duration rateLimitDelay(RestClientResponseException exception) {
        Duration headerDelay = retryAfterHeader(exception.getResponseHeaders());
        if (headerDelay != null) {
            return headerDelay;
        }
        String body = exception.getResponseBodyAsString();
        if (body != null) {
            Duration bodyDelay = matchDelay(body, RETRY_SECONDS);
            if (bodyDelay == null) {
                bodyDelay = matchDelay(body, RETRY_DELAY_FIELD);
            }
            if (bodyDelay != null) {
                return bodyDelay;
            }
        }
        return nonNegative(routingProperties.getGeminiDefaultRateLimitDelay(), Duration.ofSeconds(30));
    }

    private Duration matchDelay(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        double seconds = Double.parseDouble(matcher.group(1));
        return Duration.ofMillis(Math.max(1L, (long) Math.ceil(seconds * 1_000)));
    }

    private Duration retryAfterHeader(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Duration transientDelay() {
        return nonNegative(routingProperties.getGeminiDefaultTransientDelay(), Duration.ofSeconds(2));
    }

    private Duration retryDelay(Instant earliestRetryAt) {
        if (earliestRetryAt == null) {
            return null;
        }
        Duration delay = Duration.between(clock.instant(), earliestRetryAt);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }
        Duration maxDelay = nonNegative(routingProperties.getGeminiMaxRetryDelay(), Duration.ofSeconds(60));
        return delay.compareTo(maxDelay) <= 0 ? delay : null;
    }

    private Duration nonNegative(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }

    private Instant earlier(Instant current, Instant candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeminiModelRoutingException("Gemini 재시도 대기가 중단되었습니다.", exception, false);
        }
    }

    private GeminiModelRoutingException exhausted(String operation, Throwable cause) {
        return new GeminiModelRoutingException(
                "Gemini " + operation + "에 사용할 수 있는 모델이 없습니다.", cause, false);
    }

    @FunctionalInterface
    public interface CandidateCall<T> {
        T call(String model);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final class ModelState {
        private final ReentrantLock lock = new ReentrantLock(true);
        private volatile Instant cooldownUntil = Instant.EPOCH;
        private volatile boolean unsupported;
    }

    private record AttemptResult<T>(T value, Instant earliestRetryAt, Throwable lastFailure) {
    }
}
