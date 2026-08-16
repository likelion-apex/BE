package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import domain.beauty.shortform.config.YouTubeProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YouTubeMetadataClientTest {

    @Test
    void acceptsExactlyFiveMinutePublicVideo() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response("PT5M"), MediaType.APPLICATION_JSON));

        YouTubeVideoMetadata metadata = fixture.client.validate("t1S24pgO2XQ");

        assertThat(metadata.duration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(metadata.title()).isEqualTo("테스트 스킨케어 루틴");
        assertThat(metadata.publisher()).isEqualTo("테스트 채널");
        assertThat(metadata.viewCount()).isEqualTo(new BigInteger("123456"));
        assertThat(metadata.thumbnailUrl()).isEqualTo("https://img.example.test/video-maxres.jpg");
        fixture.server.verify();
    }

    @Test
    void requestsStatisticsAndFallsBackToAvailableThumbnail() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("statistics")))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "contentDetails": {"duration": "PT58S"},
                            "status": {"privacyStatus": "public", "uploadStatus": "processed"},
                            "snippet": {
                              "title": "제목",
                              "channelTitle": "게시자",
                              "thumbnails": {"default": {"url": "https://img.example.test/default.jpg"}}
                            },
                            "statistics": {}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        YouTubeVideoMetadata metadata = fixture.client.validate("t1S24pgO2XQ");

        assertThat(metadata.thumbnailUrl()).isEqualTo("https://img.example.test/default.jpg");
        assertThat(metadata.viewCount()).isNull();
        fixture.server.verify();
    }

    @Test
    void rejectsVideoLongerThanFiveMinutesBeforeAiCall() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(response("PT5M1S"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_VIDEO_TOO_LONG));
        fixture.server.verify();
    }

    @Test
    void requiresYoutubeApiKey() {
        YouTubeProperties properties = new YouTubeProperties();
        properties.setApiKey("");
        YouTubeMetadataClient client = new YouTubeMetadataClient(RestClient.create(), properties);

        assertThatThrownBy(() -> client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_CONFIGURATION_MISSING));
    }

    @Test
    void rejectsUnavailableOrUnprocessedVideo() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(response("PT58S", "private", "processed"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE));
        fixture.server.verify();
    }

    @Test
    void rejectsVideoThatHasNotFinishedProcessing() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(response("PT58S", "public", "uploaded"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE));
        fixture.server.verify();
    }

    @Test
    void mapsYoutubeApiFailureToExternalApiError() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE));
        fixture.server.verify();
    }

    @Test
    void rejectsEmptyApiResult() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE));
        fixture.server.verify();
    }

    @Test
    void rejectsInvalidDuration() {
        TestFixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(response("invalid"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.validate("t1S24pgO2XQ"))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("재생 시간");
                });
        fixture.server.verify();
    }

    private TestFixture fixture() {
        YouTubeProperties properties = new YouTubeProperties();
        properties.setApiKey("test-key");
        properties.setMaxDuration(Duration.ofMinutes(5));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestFixture(new YouTubeMetadataClient(builder.build(), properties), server);
    }

    private String response(String duration) {
        return response(duration, "public", "processed");
    }

    private String response(String duration, String privacyStatus, String uploadStatus) {
        return """
                {
                  "items": [{
                    "contentDetails": {"duration": "%s"},
                    "status": {"privacyStatus": "%s", "uploadStatus": "%s"},
                    "snippet": {
                      "title": "테스트 스킨케어 루틴",
                      "channelTitle": "테스트 채널",
                      "thumbnails": {
                        "maxres": {"url": "https://img.example.test/video-maxres.jpg"},
                        "high": {"url": "https://img.example.test/video.jpg"}
                      }
                    },
                    "statistics": {"viewCount": "123456"}
                  }]
                }
                """.formatted(duration, privacyStatus, uploadStatus);
    }

    private record TestFixture(YouTubeMetadataClient client, MockRestServiceServer server) {
    }
}
