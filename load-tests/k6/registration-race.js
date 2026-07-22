import exec from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const PARTICIPANT_TYPE_ID = __ENV.PARTICIPANT_TYPE_ID || '';
const CHURCH_ID = __ENV.CHURCH_ID || '';
const SINGLE_AM_LECTURE_ID = __ENV.AM_LECTURE_ID || '';
const SINGLE_PM_LECTURE_ID = __ENV.PM_LECTURE_ID || '';
const SINGLE_EXPECTED_SUCCESSES = positiveIntegerEnv('EXPECTED_SUCCESSES', 10);
const RAW_LECTURE_PAIRS = (__ENV.LECTURE_PAIRS || '').trim();
const MULTI_PAIR_MODE = RAW_LECTURE_PAIRS.length > 0;
const LECTURE_PAIRS = MULTI_PAIR_MODE
    ? parseLecturePairs(RAW_LECTURE_PAIRS)
    : [lecturePair(
        SINGLE_AM_LECTURE_ID,
        SINGLE_PM_LECTURE_ID,
        SINGLE_EXPECTED_SUCCESSES,
        true
    )];
const MAX_EXPECTED_SUCCESSES_PER_PAIR = Math.max(
    ...LECTURE_PAIRS.map(pair => pair.expectedSuccesses)
);
const EARLY_USERS_PER_PAIR = MULTI_PAIR_MODE
    ? positiveIntegerEnv('EARLY_USERS_PER_PAIR', MAX_EXPECTED_SUCCESSES_PER_PAIR + 20)
    : positiveIntegerEnv('EARLY_USERS', SINGLE_EXPECTED_SUCCESSES + 20);
const LATE_USERS_PER_PAIR = MULTI_PAIR_MODE
    ? positiveIntegerEnv('LATE_USERS_PER_PAIR', 100)
    : positiveIntegerEnv('LATE_USERS', 100);
const EARLY_USERS = EARLY_USERS_PER_PAIR * LECTURE_PAIRS.length;
const LATE_USERS = LATE_USERS_PER_PAIR * LECTURE_PAIRS.length;
const EXPECTED_SUCCESSES = LECTURE_PAIRS.reduce(
    (total, pair) => total + pair.expectedSuccesses,
    0
);
const PREPARE_SECONDS = positiveIntegerEnv('PREPARE_SECONDS', 30);
const LATE_DELAY_MS = positiveIntegerEnv('LATE_DELAY_MS', 1000);
const PHONE_SEQUENCE_START = positiveIntegerEnv('PHONE_SEQUENCE_START', 70000000);
const RUN_ID = (__ENV.RUN_ID || 'race').replace(/[^a-zA-Z0-9_-]/g, '');

const registrationPrepared = new Rate('registration_prepared');
const preparedBeforeRace = new Rate('prepared_before_race');
const registrationSuccess = new Counter('registration_success');
const capacityRejection = new Counter('capacity_rejection');
const lateCapacityRejection = new Rate('late_capacity_rejection');
const lateAdmissionViolation = new Counter('late_admission_violation');
const unexpectedFlowError = new Counter('unexpected_flow_error');
const completionPageSuccess = new Rate('completion_page_success');
const holdDuration = new Trend('hold_duration', true);
const finalizeDuration = new Trend('finalize_duration', true);

export const options = {
    discardResponseBodies: true,
    scenarios: {
        early_applicants: {
            executor: 'per-vu-iterations',
            exec: 'earlyApplicant',
            vus: EARLY_USERS,
            iterations: 1,
            maxDuration: '3m'
        },
        late_applicants: {
            executor: 'per-vu-iterations',
            exec: 'lateApplicant',
            vus: LATE_USERS,
            iterations: 1,
            maxDuration: '3m'
        }
    },
    thresholds: buildThresholds()
};

function buildThresholds() {
    const thresholds = {
        'registration_prepared': ['rate>0.999'],
        'prepared_before_race': ['rate==1'],
        'registration_success': [`count==${EXPECTED_SUCCESSES}`],
        'late_capacity_rejection': ['rate==1'],
        'late_admission_violation': ['count==0'],
        'unexpected_flow_error': ['count==0'],
        'completion_page_success': ['rate==1'],
        'hold_duration': ['p(95)<5000', 'p(99)<10000'],
        'finalize_duration': ['p(95)<5000', 'p(99)<10000'],
        'http_req_failed{endpoint:register_page}': ['rate==0'],
        'http_req_failed{endpoint:registration_form}': ['rate==0'],
        'http_req_failed{endpoint:finalize}': ['rate==0'],
        'http_req_failed{endpoint:complete_page}': ['rate==0']
    };

    LECTURE_PAIRS.forEach(pair => {
        const selector = `lecture_pair:${pair.key}`;
        thresholds[`registration_success{${selector}}`] = [
            `count==${pair.expectedSuccesses}`
        ];
        thresholds[`late_capacity_rejection{${selector}}`] = ['rate==1'];
        thresholds[`late_admission_violation{${selector}}`] = ['count==0'];
        thresholds[`unexpected_flow_error{${selector}}`] = ['count==0'];
        thresholds[`completion_page_success{${selector}}`] = ['rate==1'];
    });

    return thresholds;
}

export function setup() {
    requireConfirmation('registration-race');
    requireValue('BASE_URL', BASE_URL);
    requireValue('PARTICIPANT_TYPE_ID', PARTICIPANT_TYPE_ID);
    requireValue('CHURCH_ID', CHURCH_ID);

    if (LECTURE_PAIRS.some(pair => !pair.amLectureId && !pair.pmLectureId)) {
        throw new Error('각 강좌 쌍에는 오전 또는 오후 강좌 ID가 하나 이상 필요합니다.');
    }
    if (LECTURE_PAIRS.some(pair => EARLY_USERS_PER_PAIR < pair.expectedSuccesses)) {
        throw new Error('강좌 쌍별 선행 사용자 수는 각 기대 성공 수 이상이어야 합니다.');
    }
    if (PHONE_SEQUENCE_START + EARLY_USERS + LATE_USERS > 99999999) {
        throw new Error('PHONE_SEQUENCE_START와 사용자 수의 합은 8자리를 넘을 수 없습니다.');
    }

    const response = http.get(`${BASE_URL}/home`, {
        redirects: 0,
        responseType: 'text',
        tags: { endpoint: 'preflight' }
    });

    if (response.status !== 200 || !isRecruitmentOpen(response.body)) {
        throw new Error('현재 모집이 OPEN 상태가 아닙니다. 모집 오픈 후 신청 경쟁 테스트를 실행하세요.');
    }

    const raceAtMs = __ENV.RACE_AT
        ? Date.parse(__ENV.RACE_AT)
        : Date.now() + PREPARE_SECONDS * 1000;

    if (Number.isNaN(raceAtMs) || raceAtMs - Date.now() < 10000) {
        throw new Error('RACE_AT은 현재보다 최소 10초 이후의 ISO-8601 시각이어야 합니다.');
    }

    LECTURE_PAIRS.forEach(pair => {
        const tags = pairTags(pair);
        lateAdmissionViolation.add(0, tags);
        unexpectedFlowError.add(0, tags);
    });

    return { raceAtMs };
}

export function earlyApplicant(data) {
    executeApplicationFlow(data.raceAtMs, false, currentLecturePair());
}

export function lateApplicant(data) {
    executeApplicationFlow(data.raceAtMs + LATE_DELAY_MS, true, currentLecturePair());
}

function executeApplicationFlow(requestAtMs, late, pair) {
    const applicant = createApplicant(late, pair);
    const metricTags = { wave: wave(late), ...pairTags(pair) };

    if (!prepareRegistration(applicant, late, pair)) {
        registrationPrepared.add(false, metricTags);
        unexpectedFlowError.add(1, { phase: 'prepare', ...metricTags });
        if (late) {
            lateCapacityRejection.add(false, metricTags);
        }
        return;
    }

    registrationPrepared.add(true, metricTags);
    const preparedInTime = Date.now() < requestAtMs;
    preparedBeforeRace.add(preparedInTime, metricTags);
    if (!preparedInTime) {
        unexpectedFlowError.add(1, { phase: 'late_prepare', ...metricTags });
        if (late) {
            lateCapacityRejection.add(false, metricTags);
        }
        return;
    }
    waitUntil(requestAtMs);

    const heldLectureIds = [];
    const lectureIds = [pair.amLectureId, pair.pmLectureId].filter(Boolean);

    for (const lectureId of lectureIds) {
        const holdResult = holdLecture(lectureId, late, pair);

        if (!holdResult.success) {
            releaseHeldLectures(heldLectureIds, late, pair);

            if (holdResult.capacityRejected) {
                capacityRejection.add(1, { lectureId, ...metricTags });
                if (late) {
                    lateCapacityRejection.add(true, metricTags);
                }
            } else {
                unexpectedFlowError.add(1, {
                    phase: 'hold',
                    lectureId,
                    ...metricTags
                });
                if (late) {
                    lateCapacityRejection.add(false, metricTags);
                }
            }
            return;
        }

        heldLectureIds.push(lectureId);
    }

    const finalizeResult = finalizeRegistration(late, pair);

    if (!finalizeResult.success) {
        releaseHeldLectures(heldLectureIds, late, pair);
        unexpectedFlowError.add(1, { phase: 'finalize', ...metricTags });
        if (late) {
            lateCapacityRejection.add(false, metricTags);
        }
        return;
    }

    registrationSuccess.add(1, metricTags);
    if (late) {
        lateAdmissionViolation.add(1, metricTags);
        lateCapacityRejection.add(false, metricTags);
    }
    verifyCompletionPage(applicant, late, pair);
}

function prepareRegistration(applicant, late, pair) {
    const metricTags = { wave: wave(late), ...pairTags(pair) };
    const page = http.get(`${BASE_URL}/register`, {
        redirects: 0,
        responseType: 'text',
        responseCallback: http.expectedStatuses(200),
        tags: { endpoint: 'register_page', ...metricTags }
    });
    const pageReady = page.status === 200
        && page.body.includes(`value="${PARTICIPANT_TYPE_ID}"`)
        && page.body.includes(`value="${CHURCH_ID}"`);

    if (!pageReady) {
        return false;
    }

    const response = http.post(
        `${BASE_URL}/lecture`,
        {
            name: applicant.name,
            participantTypeId: PARTICIPANT_TYPE_ID,
            churchId: CHURCH_ID,
            phoneNumber: applicant.phoneNumber
        },
        {
            redirects: 0,
            responseType: 'none',
            responseCallback: http.expectedStatuses(302),
            tags: { endpoint: 'registration_form', ...metricTags }
        }
    );

    const location = response.headers.Location || '';

    return response.status === 302 && location.endsWith('/lecture');
}

function holdLecture(lectureId, late, pair) {
    const response = http.post(
        `${BASE_URL}/lectures/apply`,
        { lectureId },
        ajaxParams('hold', late, pair, http.expectedStatuses(200, 400, 409), lectureId)
    );
    holdDuration.add(response.timings.duration, {
        wave: wave(late),
        lectureId,
        ...pairTags(pair)
    });
    const body = parseJson(response);

    return {
        success: response.status === 200 && body?.success === true,
        capacityRejected: response.status === 400
            && body?.success === false
            && body?.message === '신청 가능한 자리가 없습니다.'
    };
}

function finalizeRegistration(late, pair) {
    const response = http.post(
        `${BASE_URL}/lectures/finalize`,
        null,
        ajaxParams('finalize', late, pair, http.expectedStatuses(200, 400, 409))
    );
    finalizeDuration.add(response.timings.duration, {
        wave: wave(late),
        ...pairTags(pair)
    });
    const body = parseJson(response);

    return {
        success: response.status === 200
            && body?.success === true
            && Number.isInteger(body?.participantLectureId)
    };
}

function verifyCompletionPage(applicant, late, pair) {
    const metricTags = { wave: wave(late), ...pairTags(pair) };
    const response = http.get(`${BASE_URL}/registration/complete`, {
        redirects: 0,
        responseType: 'text',
        responseCallback: http.expectedStatuses(200),
        tags: { endpoint: 'complete_page', ...metricTags }
    });
    const successful = response.status === 200
        && response.body.includes('신청이 완료되었습니다')
        && response.body.includes(applicant.name);

    completionPageSuccess.add(successful, metricTags);
    check(response, {
        '최종 완료 페이지에 신청자 표시': () => successful
    });

    if (!successful) {
        unexpectedFlowError.add(1, { phase: 'complete_page', ...metricTags });
    }
}

function releaseHeldLectures(lectureIds, late, pair) {
    lectureIds.forEach(lectureId => {
        const response = http.post(
            `${BASE_URL}/lectures/cancel`,
            { lectureId },
            ajaxParams('cancel', late, pair, http.expectedStatuses(200, 400, 409), lectureId)
        );
        const body = parseJson(response);

        if (response.status !== 200 || body?.success !== true) {
            unexpectedFlowError.add(1, {
                phase: 'cancel',
                wave: wave(late),
                lectureId,
                ...pairTags(pair)
            });
        }
    });
}

function ajaxParams(endpoint, late, pair, responseCallback, lectureId = '') {
    return {
        redirects: 0,
        responseType: 'text',
        responseCallback,
        headers: {
            'X-Requested-With': 'XMLHttpRequest'
        },
        tags: {
            endpoint,
            wave: wave(late),
            lectureId,
            ...pairTags(pair)
        }
    };
}

function createApplicant(late, pair) {
    const sequence = PHONE_SEQUENCE_START + exec.vu.idInTest;

    return {
        name: `loadtest-${RUN_ID}-${wave(late)}-${pair.key}-${exec.vu.idInTest}`,
        phoneNumber: `010${String(sequence).padStart(8, '0')}`
    };
}

function currentLecturePair() {
    const pairIndex = Number(exec.scenario.iterationInTest) % LECTURE_PAIRS.length;

    return LECTURE_PAIRS[pairIndex];
}

function pairTags(pair) {
    return { lecture_pair: pair.key };
}

function parseLecturePairs(value) {
    const pairs = value.split(',').map((entry, index) => {
        const parts = entry.split(':').map(part => part.trim());

        if (parts.length !== 3) {
            throw new Error(
                `LECTURE_PAIRS의 ${index + 1}번째 값은 오전ID:오후ID:기대성공수 형식이어야 합니다.`
            );
        }

        return lecturePair(
            parts[0],
            parts[1],
            positiveInteger(parts[2], `LECTURE_PAIRS ${index + 1}번째 기대성공수`)
        );
    });
    const usedLectureIds = new Set();

    pairs.forEach(pair => {
        [pair.amLectureId, pair.pmLectureId].filter(Boolean).forEach(lectureId => {
            if (usedLectureIds.has(lectureId)) {
                throw new Error(`강좌 ID ${lectureId}가 LECTURE_PAIRS에 중복 지정됐습니다.`);
            }
            usedLectureIds.add(lectureId);
        });
    });

    return pairs;
}

function lecturePair(amLectureId, pmLectureId, expectedSuccesses, allowEmpty = false) {
    const normalizedAmLectureId = normalizeLectureId(amLectureId, '오전 강좌 ID');
    const normalizedPmLectureId = normalizeLectureId(pmLectureId, '오후 강좌 ID');

    if (!allowEmpty && !normalizedAmLectureId && !normalizedPmLectureId) {
        throw new Error('각 강좌 쌍에는 오전 또는 오후 강좌 ID가 하나 이상 필요합니다.');
    }
    if (normalizedAmLectureId && normalizedAmLectureId === normalizedPmLectureId) {
        throw new Error(`강좌 ID ${normalizedAmLectureId}를 오전과 오후에 동시에 지정할 수 없습니다.`);
    }

    return {
        amLectureId: normalizedAmLectureId,
        pmLectureId: normalizedPmLectureId,
        expectedSuccesses,
        key: `am-${normalizedAmLectureId || 'none'}-pm-${normalizedPmLectureId || 'none'}`
    };
}

function normalizeLectureId(value, name) {
    const normalized = String(value || '').trim();

    if (normalized === '' || normalized === '-') {
        return '';
    }
    if (!/^\d+$/.test(normalized) || Number.parseInt(normalized, 10) <= 0) {
        throw new Error(`${name}은 양의 정수 또는 - 이어야 합니다.`);
    }

    return normalized;
}

function isRecruitmentOpen(body) {
    return body.includes('현재 특강 신청을 받고 있습니다.')
        && body.includes('href="/register"');
}

function parseJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function waitUntil(targetMs) {
    while (Date.now() < targetMs) {
        sleep(Math.min(targetMs - Date.now(), 1000) / 1000);
    }
}

function wave(late) {
    return late ? 'late' : 'early';
}

function requireConfirmation(expected) {
    if (__ENV.CONFIRM_LOAD_TEST !== expected) {
        throw new Error(`실행하려면 CONFIRM_LOAD_TEST=${expected}를 지정해야 합니다.`);
    }
}

function requireValue(name, value) {
    if (value === undefined || value === null || String(value).trim() === '') {
        throw new Error(`${name} 환경변수가 필요합니다.`);
    }
}

function positiveIntegerEnv(name, defaultValue) {
    return positiveInteger(__ENV[name] || String(defaultValue), name);
}

function positiveInteger(rawValue, name) {
    const value = Number.parseInt(rawValue, 10);

    if (!/^\d+$/.test(String(rawValue)) || !Number.isInteger(value) || value <= 0) {
        throw new Error(`${name}은 양의 정수여야 합니다.`);
    }

    return value;
}
