package feat.apex_BE.product.dto.request;

import java.util.List;

/**
 * Request payload for Google Cloud Vision's `images:annotate` endpoint.
 * https://cloud.google.com/vision/docs/reference/rest/v1/images/annotate
 */
public record GoogleVisionAnnotateRequest(
        List<AnnotateImageRequest> requests
) {

    public record AnnotateImageRequest(Image image, List<Feature> features) {
    }

    public record Image(ImageSource source) {
    }

    public record ImageSource(String imageUri) {
    }

    public record Feature(String type) {
    }

    public static GoogleVisionAnnotateRequest textDetection(String imageUrl) {
        return new GoogleVisionAnnotateRequest(List.of(
                new AnnotateImageRequest(
                        new Image(new ImageSource(imageUrl)),
                        List.of(new Feature("TEXT_DETECTION"))
                )
        ));
    }
}
