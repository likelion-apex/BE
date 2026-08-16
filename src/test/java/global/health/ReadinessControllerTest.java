package global.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;

class ReadinessControllerTest {

    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final ReadinessController controller = new ReadinessController(availability);

    @Test
    void returnsNoContentOnlyAfterTheApplicationAcceptsTraffic() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

        assertThat(controller.readiness().getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void returnsServiceUnavailableDuringStartupOrShutdown() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);

        assertThat(controller.readiness().getStatusCode().value()).isEqualTo(503);
    }
}
