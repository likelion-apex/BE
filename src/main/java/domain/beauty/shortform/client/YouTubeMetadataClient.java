package domain.beauty.shortform.client;

import domain.beauty.shortform.config.YouTubeProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.math.BigInteger;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class YouTubeMetadataClient {

    private final RestClient restClient;
    private final YouTubeProperties properties;

    public YouTubeMetadataClient(
            @Qualifier("youtubeMetadataRestClient") RestClient restClient,
            YouTubeProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public YouTubeVideoMetadata validate(String videoId) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "YOUTUBE_API_KEY 환경변수가 필요합니다."
            );
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/youtube/v3/videos")
                            .queryParam("part", "contentDetails,status,snippet,statistics")
                            .queryParam("id", videoId)
                            .queryParam("key", properties.getApiKey())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode item = response == null ? null : response.path("items").path(0);
            if (item == null || item.isMissingNode() || item.isEmpty()) {
                throw new CustomException(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE);
            }

            String privacyStatus = item.path("status").path("privacyStatus").stringValue("");
            String uploadStatus = item.path("status").path("uploadStatus").stringValue("");
            if (!"public".equals(privacyStatus) || !"processed".equals(uploadStatus)) {
                throw new CustomException(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE);
            }

            Duration duration = Duration.parse(item.path("contentDetails").path("duration").stringValue(""));
            if (duration.compareTo(properties.getMaxDuration()) > 0) {
                throw new CustomException(
                        ErrorCode.SHORTFORM_VIDEO_TOO_LONG,
                        "최대 5분 이하의 YouTube 영상만 분석할 수 있습니다."
                );
            }

            JsonNode snippet = item.path("snippet");
            return new YouTubeVideoMetadata(
                    videoId,
                    duration,
                    snippet.path("title").stringValue(""),
                    selectThumbnailUrl(snippet.path("thumbnails")),
                    snippet.path("channelTitle").stringValue(""),
                    parseViewCount(item.path("statistics").path("viewCount").stringValue(null))
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (DateTimeParseException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_VIDEO_UNAVAILABLE, "영상 재생 시간을 확인할 수 없습니다.");
        } catch (RestClientException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE, "YouTube 영상 정보를 확인할 수 없습니다.");
        }
    }

    private String selectThumbnailUrl(JsonNode thumbnails) {
        for (String quality : List.of("maxres", "standard", "high", "medium", "default")) {
            String url = thumbnails.path(quality).path("url").stringValue(null);
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private BigInteger parseViewCount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigInteger(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
