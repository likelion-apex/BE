package domain.routine;

import static org.assertj.core.api.Assertions.assertThat;

import domain.routine.domain.DailyCondition;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class DailyConditionMappingTest {

    @Test
    void mapsConditionToNonReservedColumnName() throws NoSuchFieldException {
        Field condition = DailyCondition.class.getDeclaredField("condition");
        Column column = condition.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo("condition_type");
        assertThat(column.nullable()).isFalse();
    }
}
