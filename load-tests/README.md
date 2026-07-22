# 배포 부하 테스트

이 테스트는 실제 모집과 신청 데이터를 변경한다. 운영 모집이 아닌 전용 리허설 모집과 전용 DB에서 실행한다.

## 사전 조건

- 부하 발생기는 Spring·Nginx 서버 및 MySQL 서버와 분리한다.
- 부하 발생기에 `k6`를 설치하고 `ulimit -n 65535`를 적용한다.
- 리허설 모집에 교회, 신청 가능한 참가자 타입, 오전·오후 강좌를 준비한다.
- 경쟁 대상 강좌의 기존 확정 신청과 활성 임시점유를 모두 확인한다.
- `EXPECTED_SUCCESSES`는 대상 강좌 중 가장 작은 **현재 남은 자리**로 설정한다.
- 참가자 타입이 오전·오후를 모두 요구한다면 `AM_LECTURE_ID`, `PM_LECTURE_ID`를 모두 전달한다.

한 부하 발생기에서 3,000 VU를 바로 실행하지 말고 100 → 500 → 1,000 → 3,000 순서로 올린다. 부하 발생기 자체의 CPU가 포화되면 서버 결과로 사용할 수 없다.

## 1. 모집 오픈 새로고침

`OPEN_AT`은 관리자에 설정한 모집 시작 시각과 동일한 ISO-8601 시각이다. 테스트는 오픈 500ms 전과 오픈 1.5초 후에 각 브라우저가 `/home`을 요청하고, 별도 관찰 VU가 실제 신청 버튼 노출 시각을 측정한다.

```bash
cd load-tests

BASE_URL=https://example.com \
OPEN_AT=2026-07-25T14:00:00+09:00 \
REFRESH_VUS=3000 \
RUN_ID=opening-rehearsal-01 \
CONFIRM_LOAD_TEST=opening-refresh \
k6 run k6/opening-refresh.js
```

서로 다른 브라우저의 최초 접속까지 포함하려면 `INCLUDE_STATIC_ASSETS=true`를 추가한다. 이 경우 VU당 CSS·JS 4개가 추가되므로 Nginx 정적 파일 처리도 함께 검증된다.

성공 기준:

- `open_transition_on_time`: `100%`
- `post_open_visible`: `100%`
- `home_duration{phase:post_open}`: p95 1초 미만, p99 2초 미만
- `http_req_failed{endpoint:home}`: 0.1% 미만

## 2. 신청 정원 경쟁

각 VU는 독립 세션으로 `/register` → 인적사항 저장 → 임시점유 → 최종확정 → 완료 페이지 확인을 수행한다.

- 선행군은 `RACE_AT`에 동시에 점유를 요청한다.
- 후행군은 기본 1초 뒤 요청한다.
- 선행군 수는 정원보다 넉넉하게 잡아 먼저 정원을 채운다.
- 성공 신청 수가 `EXPECTED_SUCCESSES`와 다르거나 후행군이 한 명이라도 성공하면 테스트가 실패한다.
- 정원 부족이 아닌 락 타임아웃, 500, 세션 오류도 실패로 분류한다.

```bash
BASE_URL=https://example.com \
PARTICIPANT_TYPE_ID=1 \
CHURCH_ID=1 \
AM_LECTURE_ID=10 \
PM_LECTURE_ID=20 \
EXPECTED_SUCCESSES=100 \
EARLY_USERS=300 \
LATE_USERS=700 \
RACE_AT=2026-07-25T14:02:00+09:00 \
LATE_DELAY_MS=1000 \
PHONE_SEQUENCE_START=71000000 \
RUN_ID=registration-rehearsal-01 \
CONFIRM_LOAD_TEST=registration-race \
k6 run k6/registration-race.js
```

`RACE_AT`을 생략하면 setup 완료 30초 후에 경쟁을 시작한다. VU가 1,000개 이상이면 명시적으로 30초 이상 여유 있는 시각을 주는 편이 안전하다.

여러 오전·오후 강좌 쌍을 동시에 경쟁시키려면 `LECTURE_PAIRS`를 사용한다. 각 값은 `오전ID:오후ID:기대성공수` 형식이며, 기대 성공 수는 두 강좌 중 더 적은 현재 남은 자리와 같아야 한다. 서로 다른 쌍에서 같은 강좌 ID를 중복 사용할 수 없다.

```bash
BASE_URL=https://example.com \
PARTICIPANT_TYPE_ID=1 \
CHURCH_ID=1 \
LECTURE_PAIRS='10:20:50,11:21:50,12:22:30' \
EARLY_USERS_PER_PAIR=100 \
LATE_USERS_PER_PAIR=100 \
RACE_AT=2026-07-25T14:02:00+09:00 \
LATE_DELAY_MS=1000 \
PHONE_SEQUENCE_START=72000000 \
RUN_ID=multi-registration-rehearsal-01 \
CONFIRM_LOAD_TEST=registration-race \
k6 run k6/registration-race.js
```

위 예시는 각 강좌 쌍에 선행 100명과 후행 100명을 배치한다. 전체 VU는 `강좌 쌍 수 × (EARLY_USERS_PER_PAIR + LATE_USERS_PER_PAIR)`이며, 전체 성공 수뿐 아니라 강좌 쌍별 성공 수와 후행 요청 거절도 각각 검증한다.

성공 기준:

- `registration_success`: `EXPECTED_SUCCESSES`와 정확히 일치
- 다중 쌍의 `registration_success{lecture_pair:...}`: 해당 쌍의 기대 성공 수와 정확히 일치
- `prepared_before_race`: `100%`
- `late_capacity_rejection`: `100%`
- `late_admission_violation`: `0`
- `unexpected_flow_error`: `0`
- `completion_page_success`: `100%`

## 3. 일반 사용자 혼합 흐름

홈, 전체 강좌, 신청 내역 조회, 인적사항 입력 후 이탈, 임시점유 후 해제를 한 번에 섞어 실제 사용자 흐름과 유사한 DB 부하를 만든다. `HOLD_LECTURE_IDS`에는 신청 가능하고 남은 자리가 있는 강좌를 여러 개 전달해 잠금 경합을 분산한다.

```bash
BASE_URL=https://example.com \
PARTICIPANT_TYPE_ID=1 \
CHURCH_ID=1 \
HOLD_LECTURE_IDS='1,3,4,5,6,7,8,9,11,12,13,14,15,16,17,18' \
BROWSE_USERS=350 \
LOOKUP_USERS=300 \
REGISTRATION_USERS=250 \
HOLD_RELEASE_USERS=100 \
RUN_ID=user-journey-rehearsal-01 \
CONFIRM_LOAD_TEST=user-journey \
k6 run k6/user-journey.js
```

이 구성은 총 1,000개의 독립 브라우저 세션을 동시에 시작한다.

- 조회 사용자는 `/home`과 `/lectures` 및 참가자 타입 필터를 조회한다.
- 검색 사용자는 `/lookup`에서 존재하지 않는 인적사항을 두 번 조회해 복합 인덱스와 세션 복원 흐름을 검증한다.
- 신청 이탈 사용자는 `/register` → 인적사항 입력 → `/lecture` → 뒤로가기 → 신청 취소를 수행한다.
- 점유 사용자는 열린 강좌 하나를 점유하고 상태를 재조회한 뒤 해제하여 draft 생성·조회·삭제와 강좌 행 잠금을 검증한다.

쓰기 없이 조회 흐름만 먼저 실행하려면 `HOLD_RELEASE_USERS=0`으로 지정한다. 점유 사용자가 있으면 setup 단계에서 한 건을 실제 점유·해제해 모집 시간과 API 상태를 사전 검증한다.

성공 기준:

- 각 `journey_success{flow:...}`: 99.5% 초과
- `unexpected_flow_error`: 0
- `lookup_miss_success`, `registration_abandon_success`: 99.5% 초과
- `hold_success`, `release_success`, `empty_draft_after_release`: 99.5% 초과
- 홈 p95 1.5초 미만, 전체 강좌·조회 p95 2초 미만, 신청 화면 p95 2.5초 미만

## 4. DB 무결성 확인

k6 성공 여부와 별개로 MySQL에서 확정 신청 수, 저장 카운터, 정원 초과 여부를 확인한다.

```bash
mysql \
  -h DB_PRIVATE_IP \
  -u career_camp \
  -p \
  career_camp \
  -e "SET @recruitment_id=1; SET @run_id='registration-rehearsal-01'; SOURCE sql/verify-registration-race.sql;"
```

모든 대상 강좌의 `integrity_status`가 `OK`이고, `load_test_application_count`가 `EXPECTED_SUCCESSES`와 같아야 한다. `late_application_count`는 0이어야 하며 마지막 조회 결과도 0행이어야 한다.

## 운영 관찰 항목

테스트 중에는 다음을 동시에 기록한다.

- Nginx: active connections, 499/502/503/504 수, upstream response time
- Spring: 컨테이너 CPU·메모리, Tomcat busy thread, Hikari active/pending connection
- MySQL: CPU, `Threads_connected`, `Threads_running`, row lock wait 및 deadlock
- 부하 발생기: CPU 80% 미만, 네트워크 대역폭, dropped iterations

클라이언트가 먼저 보낸 순서와 서버 도착 순서는 네트워크 상황에 따라 다를 수 있다. 이 테스트의 후행군 판정은 `LATE_DELAY_MS`만큼 실제 요청 시점을 분리해, 정원이 이미 점유된 뒤 들어온 요청이 반드시 거절되는지를 검증한다.
