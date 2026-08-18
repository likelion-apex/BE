package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class GeminiModelRouterTest {

    @Test
    void usesOperationSpecificModelOrders() {
        GeminiProperties gemini = properties("gemini-3.6-flash");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        GeminiModelRouter router = router(gemini, routing);

        List<String> video = attemptedOrder(router, GeminiRouteProfile.VIDEO);
        List<String> text = attemptedOrder(router, GeminiRouteProfile.TEXT);
        List<String> product = attemptedOrder(router, GeminiRouteProfile.PRODUCT);

        assertThat(video).containsExactly(
                "gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite", "gemini-3-flash-preview");
        assertThat(text).containsExactly(
                "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.5-flash",
                "gemini-3.6-flash", "gemini-3-flash-preview");
        assertThat(product).containsExactly(
                "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite",
                "gemini-3.6-flash", "gemini-3-flash-preview");
    }

    @Test
    void skipsRateLimitedModelDuringSharedCooldown() {
        GeminiProperties gemini = properties("primary");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiVideoModels(List.of("secondary"));
        MutableClock clock = new MutableClock();
        GeminiModelRouter router = new GeminiModelRouter(gemini, routing, clock, clock::advanceMillis);
        AtomicInteger primaryCalls = new AtomicInteger();

        String first = router.route(GeminiRouteProfile.VIDEO, "test", model -> {
            if (model.equals("primary")) {
                primaryCalls.incrementAndGet();
                throw response(HttpStatus.TOO_MANY_REQUESTS, "Please retry in 30s", null);
            }
            return model;
        });
        String second = router.route(GeminiRouteProfile.VIDEO, "test", model -> {
            if (model.equals("primary")) {
                primaryCalls.incrementAndGet();
            }
            return model;
        });

        assertThat(first).isEqualTo("secondary");
        assertThat(second).isEqualTo("secondary");
        assertThat(primaryCalls).hasValue(1);
    }

    @Test
    void disablesNotFoundModelAndContinues() {
        GeminiProperties gemini = properties("unused");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("retired", "available"));
        GeminiModelRouter router = router(gemini, routing);
        AtomicInteger retiredCalls = new AtomicInteger();

        for (int run = 0; run < 2; run++) {
            String result = router.route(GeminiRouteProfile.TEXT, "test", model -> {
                if (model.equals("retired")) {
                    retiredCalls.incrementAndGet();
                    throw response(HttpStatus.NOT_FOUND, "model is not available", null);
                }
                return model;
            });
            assertThat(result).isEqualTo("available");
        }
        assertThat(retiredCalls).hasValue(1);
    }

    @Test
    void stopsImmediatelyOnAuthenticationFailure() {
        GeminiProperties gemini = properties("unused");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("first", "second"));
        GeminiModelRouter router = router(gemini, routing);
        List<String> called = new ArrayList<>();

        assertThatThrownBy(() -> router.route(GeminiRouteProfile.TEXT, "test", model -> {
            called.add(model);
            throw response(HttpStatus.FORBIDDEN, "forbidden", null);
        }))
                .isInstanceOf(GeminiModelRoutingException.class)
                .satisfies(error -> assertThat(((GeminiModelRoutingException) error).isConfigurationFailure()).isTrue());
        assertThat(called).containsExactly("first");
    }

    @Test
    void waitsForEarliestRetryAfterAndRunsOnlyOneMoreCycle() {
        GeminiProperties gemini = properties("unused");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("first", "second"));
        MutableClock clock = new MutableClock();
        GeminiModelRouter router = new GeminiModelRouter(gemini, routing, clock, clock::advanceMillis);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> router.route(GeminiRouteProfile.TEXT, "test", model -> {
            calls.incrementAndGet();
            throw response(HttpStatus.TOO_MANY_REQUESTS, "Please retry in 1.25s", null);
        })).isInstanceOf(GeminiModelRoutingException.class);

        assertThat(calls).hasValue(4);
        assertThat(clock.advancedMillis()).isEqualTo(1_250);
    }

    @Test
    void doesNotWaitBeyondConfiguredMaximum() {
        GeminiProperties gemini = properties("unused");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("only"));
        MutableClock clock = new MutableClock();
        GeminiModelRouter router = new GeminiModelRouter(gemini, routing, clock, clock::advanceMillis);

        assertThatThrownBy(() -> router.route(GeminiRouteProfile.TEXT, "test", model -> {
            throw response(HttpStatus.TOO_MANY_REQUESTS, "Please retry in 61s", null);
        })).isInstanceOf(GeminiModelRoutingException.class);
        assertThat(clock.advancedMillis()).isZero();
    }

    @Test
    void serializesConcurrentCallsToTheSameModel() throws Exception {
        GeminiProperties gemini = properties("unused");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("only"));
        GeminiModelRouter router = router(gemini, routing);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> router.route(GeminiRouteProfile.TEXT, "test", model -> {
                int active = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(active, Math::max);
                entered.countDown();
                await(release);
                concurrent.decrementAndGet();
                return model;
            }));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> router.route(GeminiRouteProfile.TEXT, "test", model -> {
                int active = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(active, Math::max);
                concurrent.decrementAndGet();
                return model;
            }));
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("only");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("only");
        }
        assertThat(maxConcurrent).hasValue(1);
    }

    private List<String> attemptedOrder(GeminiModelRouter router, GeminiRouteProfile profile) {
        List<String> attempted = new ArrayList<>();
        String result = router.route(profile, "order", model -> {
            attempted.add(model);
            if (!model.equals("gemini-3-flash-preview")) {
                throw new GeminiCandidateRejectedException("next");
            }
            return model;
        });
        assertThat(result).isEqualTo("gemini-3-flash-preview");
        return attempted;
    }

    private GeminiModelRouter router(
            GeminiProperties gemini,
            ShortformAiFallbackProperties routing
    ) {
        MutableClock clock = new MutableClock();
        return new GeminiModelRouter(gemini, routing, clock, clock::advanceMillis);
    }

    private GeminiProperties properties(String model) {
        GeminiProperties properties = new GeminiProperties();
        properties.setModel(model);
        return properties;
    }

    private HttpClientErrorException response(
            HttpStatus status,
            String message,
            String retryAfter
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return HttpClientErrorException.create(
                status,
                status.getReasonPhrase(),
                headers,
                ("{\"error\":{\"message\":\"" + message + "\"}}").getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-18T00:00:00Z");
        private long advancedMillis;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advanceMillis(long millis) {
            advancedMillis += millis;
            instant = instant.plusMillis(millis);
        }

        long advancedMillis() {
            return advancedMillis;
        }
    }
}
