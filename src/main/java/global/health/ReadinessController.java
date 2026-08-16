package global.health;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class ReadinessController {

    public static final String PATH = "/internal/health/readiness";

    private final ApplicationAvailability availability;

    public ReadinessController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping(PATH)
    public ResponseEntity<Void> readiness() {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
