package feat.apex_BE.product.client;

import feat.apex_BE.product.dto.request.GoogleVisionAnnotateRequest;
import feat.apex_BE.product.dto.response.GoogleVisionAnnotateResponse;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Runs OCR (text detection) on a single image URL using Google Cloud
 * Vision's REST API. Failures are treated as soft failures (empty result)
 * since ingredient extraction is a best-effort enhancement, not a required
 * part of the product search response.
 */
@Component
public class GoogleVisionOcrClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOcrClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String annotateUri;

    public GoogleVisionOcrClient(
            RestClient.Builder restClientBuilder,
            @Value("${google.vision.api-key}") String apiKey,
            @Value("${google.vision.annotate-uri}") String annotateUri
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.annotateUri = annotateUri;
    }

    public Optional<String> extractText(String imageUrl) {
        try {
            GoogleVisionAnnotateResponse response = restClient.post()
                    .uri(annotateUri + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GoogleVisionAnnotateRequest.textDetection(imageUrl))
                    .retrieve()
                    .body(GoogleVisionAnnotateResponse.class);

            return firstResponse(response)
                    .flatMap(this::toText);
        } catch (RestClientException e) {
            log.warn("Google Vision OCR 호출에 실패했습니다. imageUrl={}, reason={}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GoogleVisionAnnotateResponse.AnnotateImageResponse> firstResponse(
            GoogleVisionAnnotateResponse response) {
        List<GoogleVisionAnnotateResponse.AnnotateImageResponse> responses =
                response == null ? null : response.responses();
        return responses == null || responses.isEmpty() ? Optional.empty() : Optional.of(responses.get(0));
    }

    private Optional<String> toText(GoogleVisionAnnotateResponse.AnnotateImageResponse annotateImageResponse) {
        if (annotateImageResponse.error() != null) {
            log.warn("Google Vision OCR 응답 오류: {}", annotateImageResponse.error().message());
            return Optional.empty();
        }
        if (annotateImageResponse.fullTextAnnotation() != null) {
            return Optional.ofNullable(annotateImageResponse.fullTextAnnotation().text());
        }
        List<GoogleVisionAnnotateResponse.TextAnnotation> textAnnotations = annotateImageResponse.textAnnotations();
        if (textAnnotations != null && !textAnnotations.isEmpty()) {
            return Optional.ofNullable(textAnnotations.get(0).description());
        }
        return Optional.empty();
    }
}
