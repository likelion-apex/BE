# Routine 운영 DB 스키마 마이그레이션

Routine 도메인의 DAY/NIGHT 분리와 DailyCondition 도입으로 발생한 운영 DB 차이를 Flyway로 보정한다.

## 마이그레이션 내용

- MariaDB 예약어인 `daily_conditions.condition`을 `condition_type`으로 사용한다.
- `routines.source_analysis_id`를 nullable로 변경한다.
- 새 DAY/NIGHT 복합 유니크 인덱스를 먼저 보장한 뒤 기존 인덱스를 제거한다.
  - 제거: `uk_routine_member_analysis_save_type`
  - 유지: `uk_routine_member_analysis_save_type_routine_type`
- 같은 날짜에 서로 다른 루틴을 기록할 수 있도록 RoutineLog의 기존 인덱스를 제거한다.
  - 제거: `uk_routine_log_member_date`
  - 유지: `uk_routine_log_member_date_routine`

## 운영 설정

prod 프로필에서는 `SPRING_FLYWAY_ENABLED=true`가 기본값이다. 기존 운영 스키마에는 Flyway 이력 테이블이 없으므로 baseline version `0`을 기록한 뒤 V1 마이그레이션을 실행한다.

로컬·테스트 프로필에서는 Flyway가 비활성화되며 Hibernate가 H2 스키마를 생성한다.
