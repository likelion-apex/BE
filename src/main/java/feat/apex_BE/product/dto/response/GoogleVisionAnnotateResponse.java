package feat.apex_BE.product.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response payload of Google Cloud Vision's `images:annotate` endpoint.
 * https://cloud.google.com/vision/docs/reference/rest/v1/images/annotate
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleVisionAnnotateResponse(
        List<AnnotateImageResponse> responses
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnnotateImageResponse(
            FullTextAnnotation fullTextAnnotation,
            List<TextAnnotation> textAnnotations,
            Error error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FullTextAnnotation(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextAnnotation(String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(Integer code, String message) {
    }
}
