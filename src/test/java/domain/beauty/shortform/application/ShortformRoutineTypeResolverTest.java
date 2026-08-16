package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.routine.domain.RoutineType;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShortformRoutineTypeResolverTest {

    private final ShortformRoutineTypeResolver resolver = new ShortformRoutineTypeResolver();

    @Test
    void keepsExplicitRoutineType() {
        assertThat(resolver.resolve(RoutineType.DAY)).isEqualTo(RoutineType.DAY);
        assertThat(resolver.resolve(RoutineType.NIGHT)).isEqualTo(RoutineType.NIGHT);
    }

    @Test
    void resolvesDayFromSixUntilBeforeEighteen() {
        assertThat(ShortformRoutineTypeResolver.resolveAt(LocalTime.of(6, 0)))
                .isEqualTo(RoutineType.DAY);
        assertThat(ShortformRoutineTypeResolver.resolveAt(LocalTime.of(17, 59, 59)))
                .isEqualTo(RoutineType.DAY);
    }

    @Test
    void resolvesNightFromEighteenUntilBeforeSix() {
        assertThat(ShortformRoutineTypeResolver.resolveAt(LocalTime.of(18, 0)))
                .isEqualTo(RoutineType.NIGHT);
        assertThat(ShortformRoutineTypeResolver.resolveAt(LocalTime.of(5, 59, 59)))
                .isEqualTo(RoutineType.NIGHT);
    }
}
