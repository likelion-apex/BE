package domain.beauty.shortform.application;

import domain.routine.RoutineType;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 숏폼 루틴 저장 요청의 DAY/NIGHT 타입을 결정한다.
 * 호출자가 타입을 명시하면 그 값을 우선하고, 기존 클라이언트 요청은 서울 시간 기준으로 보완한다.
 */
@Component
public class ShortformRoutineTypeResolver {

    static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAY_START = LocalTime.of(6, 0);
    private static final LocalTime NIGHT_START = LocalTime.of(18, 0);

    private final Clock clock;

    public ShortformRoutineTypeResolver() {
        this(Clock.system(SEOUL_ZONE));
    }

    ShortformRoutineTypeResolver(Clock clock) {
        this.clock = clock;
    }

    public RoutineType resolve(RoutineType requestedRoutineType) {
        if (requestedRoutineType != null) {
            return requestedRoutineType;
        }
        return resolveAt(LocalTime.now(clock));
    }

    static RoutineType resolveAt(LocalTime time) {
        return !time.isBefore(DAY_START) && time.isBefore(NIGHT_START)
                ? RoutineType.DAY
                : RoutineType.NIGHT;
    }
}
