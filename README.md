# career-camp

위미션 진로캠프 진로특강 신청폼

## 배포 전 확인

- 운영 DB는 기존 MySQL 인스턴스와 영구 볼륨을 계속 사용한다. 애플리케이션 재배포 시 DB 컨테이너나 볼륨을 삭제하지 않는다.
- 첫 배포 전 DB 백업을 만든다. `flyway_schema_history` 테이블은 이후 배포에서도 삭제하지 않는다.
- 현재 HTTP 세션과 페이지 이탈 시 점유 해제 예약은 애플리케이션 메모리에 있다. Spring 인스턴스는 1개로 운영한다. 다중 인스턴스가 필요하면 먼저 공유 세션과 분산 예약 저장소를 도입해야 한다.
- MySQL 연결 수는 애플리케이션 풀 크기보다 충분히 크게 둔다. 기본 풀은 최대 24개이며 `DB_POOL_MAX_SIZE`로 조정할 수 있다.

필수 운영 환경변수:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL='jdbc:mysql://127.0.0.1:3306/career_camp?useUnicode=true&characterEncoding=utf8'
DB_USERNAME='...'
DB_PASSWORD='...'
SESSION_COOKIE_SECURE=true
```

운영 프로필은 Hibernate `ddl-auto=validate`를 사용한다. 테이블을 매번 다시 만들지 않으며, Flyway가 `src/main/resources/db/migration`의 미적용 변경만 한 번씩 실행한다. DB 커넥션에는 `Asia/Seoul`, `READ_COMMITTED`, 잠금 대기 5초가 적용된다.

배포 완료 후 Nginx나 배포 도구의 readiness 확인 주소로 `/actuator/health/readiness`를 사용한다. 응답이 `UP`이 된 뒤 트래픽을 연결하고, 종료 시에는 애플리케이션의 30초 graceful shutdown이 끝날 때까지 프로세스를 강제 종료하지 않는다.
