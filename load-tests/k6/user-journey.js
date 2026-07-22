import exec from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const PARTICIPANT_TYPE_ID = (__ENV.PARTICIPANT_TYPE_ID || '').trim();
const CHURCH_ID = (__ENV.CHURCH_ID || '').trim();
const HOLD_LECTURE_IDS = parsePositiveIntegerList(__ENV.HOLD_LECTURE_IDS || '');
const BROWSE_USERS = nonNegativeIntegerEnv('BROWSE_USERS', 350);
const LOOKUP_USERS = nonNegativeIntegerEnv('LOOKUP_USERS', 300);
const REGISTRATION_USERS = nonNegativeIntegerEnv('REGISTRATION_USERS', 250);
const HOLD_RELEASE_USERS = nonNegativeIntegerEnv('HOLD_RELEASE_USERS', 100);
const MAX_DURATION = __ENV.MAX_DURATION || '3m';
const THINK_TIME_MAX_MS = nonNegativeIntegerEnv('THINK_TIME_MAX_MS', 300);
const PHONE_SEQUENCE_START = positiveIntegerEnv('PHONE_SEQUENCE_START', 80000000);
const RUN_ID = sanitizeRunId(__ENV.RUN_ID || 'journey');

const journeySuccess = new Rate('journey_success');
const unexpectedFlowError = new Counter('unexpected_flow_error');
const serverResponseError = new Counter('server_response_error');
const lookupMissSuccess = new Rate('lookup_miss_success');
const registrationAbandonSuccess = new Rate('registration_abandon_success');
const holdSuccess = new Rate('hold_success');
const releaseSuccess = new Rate('release_success');
const emptyDraftAfterRelease = new Rate('empty_draft_after_release');
const stepDuration = new Trend('journey_step_duration', true);

export const options = {
    discardResponseBodies: true,
    scenarios: buildScenarios(),
    thresholds: buildThresholds()
};

export function setup() {
    requireConfirmation('user-journey');
    requireValue('BASE_URL', BASE_URL);

    if (LOOKUP_USERS > 0 || REGISTRATION_USERS > 0 || HOLD_RELEASE_USERS > 0) {
        requireValue('CHURCH_ID', CHURCH_ID);
    }
    if (REGISTRATION_USERS > 0 || HOLD_RELEASE_USERS > 0) {
        requireValue('PARTICIPANT_TYPE_ID', PARTICIPANT_TYPE_ID);
    }
    if (HOLD_RELEASE_USERS > 0 && HOLD_LECTURE_IDS.length === 0) {
        throw new Error('HOLD_RELEASE_USERS가 1 이상이면 HOLD_LECTURE_IDS가 필요합니다.');
    }

    unexpectedFlowError.add(0);
    serverResponseError.add(0);
    const home = http.get(`${BASE_URL}/home?loadTest=${RUN_ID}-preflight`, textParams(
        'preflight_home',
        'preflight',
        http.expectedStatuses(200)
    ));

    if (home.status !== 200 || !isOpenHome(home.body)) {
        throw new Error('현재 모집이 OPEN 상태가 아니거나 홈 응답이 정상적이지 않습니다.');
    }

    if (REGISTRATION_USERS > 0 || HOLD_RELEASE_USERS > 0) {
        const register = http.get(`${BASE_URL}/register`, textParams(
            'preflight_register',
            'preflight',
            http.expectedStatuses(200)
        ));

        if (!isRegisterPageReady(register)) {
            throw new Error('신청 페이지에서 지정한 참가자 타입 또는 교회를 찾지 못했습니다.');
        }
    }

    if (HOLD_RELEASE_USERS > 0) {
        verifyWriteFlowAvailable();
    }

    return {};
}

export function browseJourney() {
    const flow = 'browse';
    const home = getPage('/home', 'home', flow);

    if (!isSuccessfulPage(home, isHomePage)) {
        return failJourney(flow, 'home');
    }
    think();

    const catalog = getPage('/lectures', 'lecture_catalog', flow);
    if (!isSuccessfulPage(catalog, isLectureCatalogPage)) {
        return failJourney(flow, 'lecture_catalog');
    }
    think();

    if (PARTICIPANT_TYPE_ID) {
        const filteredCatalog = getPage(
            `/lectures?participantTypeId=${encodeURIComponent(PARTICIPANT_TYPE_ID)}`,
            'lecture_catalog_filtered',
            flow
        );
        if (!isSuccessfulPage(filteredCatalog, isLectureCatalogPage)) {
            return failJourney(flow, 'lecture_catalog_filtered');
        }
    }

    finishJourney(flow, true);
}

export function lookupJourney() {
    const flow = 'lookup';
    const home = getPage('/home', 'home', flow);
    if (!isSuccessfulPage(home, isHomePage)) {
        return failJourney(flow, 'home');
    }
    think();

    const lookupPage = getPage('/lookup', 'lookup_page', flow);
    if (!isSuccessfulPage(lookupPage, isLookupPage)) {
        lookupMissSuccess.add(false, { flow });
        return failJourney(flow, 'lookup_page');
    }

    const applicant = createApplicant('miss');
    const lookup = timedRequest('lookup_search', flow, () => http.post(
        `${BASE_URL}/lookup`,
        {
            name: applicant.name,
            churchId: CHURCH_ID,
            phoneNumber: applicant.phoneNumber
        },
        textParams('lookup_search', flow, http.expectedStatuses(200))
    ));
    const lookupMissed = lookup.status === 200
        && lookup.body.includes('입력한 정보와 일치하는 신청 내역이 없습니다.');
    lookupMissSuccess.add(lookupMissed, { flow });
    if (!lookupMissed) {
        return failJourney(flow, 'lookup_search');
    }
    think();

    const restoredLookup = getPage('/lookup', 'lookup_restore', flow);
    if (restoredLookup.status !== 200
        || !restoredLookup.body.includes(applicant.name)
        || !restoredLookup.body.includes('입력한 정보와 일치하는 신청 내역이 없습니다.')) {
        return failJourney(flow, 'lookup_restore');
    }

    const clearLookup = getPage('/home', 'home_clear_lookup', flow);
    if (!isSuccessfulPage(clearLookup, isHomePage)) {
        return failJourney(flow, 'home_clear_lookup');
    }

    finishJourney(flow, true);
}

export function registrationAbandonJourney() {
    const flow = 'registration_abandon';
    const applicant = createApplicant('abandon');

    if (!prepareRegistration(applicant, flow)) {
        registrationAbandonSuccess.add(false, { flow });
        return failJourney(flow, 'prepare_registration');
    }
    think();

    const status = getDraftStatus(flow, 'draft_status_empty');
    if (!status.success || status.selectedLectureIds.length !== 0) {
        registrationAbandonSuccess.add(false, { flow });
        return failJourney(flow, 'draft_status_empty');
    }

    const back = timedRequest('back_to_register', flow, () => http.post(
        `${BASE_URL}/registration/back-to-register`,
        null,
        noBodyParams('back_to_register', flow, http.expectedStatuses(302))
    ));
    if (!isRedirectTo(back, '/register')) {
        registrationAbandonSuccess.add(false, { flow });
        return failJourney(flow, 'back_to_register');
    }

    const restoredRegister = getPage('/register', 'register_restored', flow);
    if (restoredRegister.status !== 200 || !restoredRegister.body.includes(applicant.name)) {
        registrationAbandonSuccess.add(false, { flow });
        return failJourney(flow, 'register_restored');
    }

    const cancelled = cancelRegistration(flow);
    registrationAbandonSuccess.add(cancelled, { flow });
    if (!cancelled) {
        return failJourney(flow, 'registration_cancel');
    }

    finishJourney(flow, true);
}

export function holdReleaseJourney() {
    const flow = 'hold_release';
    const applicant = createApplicant('hold');
    const lectureId = HOLD_LECTURE_IDS[Number(exec.scenario.iterationInTest) % HOLD_LECTURE_IDS.length];

    if (!prepareRegistration(applicant, flow)) {
        holdSuccess.add(false, { flow, lectureId });
        releaseSuccess.add(false, { flow, lectureId });
        return failJourney(flow, 'prepare_registration', { lectureId });
    }

    const held = holdLecture(lectureId, flow);
    holdSuccess.add(held.success, { flow, lectureId });
    if (!held.success) {
        cancelRegistration(flow);
        return failJourney(flow, `hold:${held.message || held.status}`, { lectureId });
    }
    think();

    const heldStatus = getDraftStatus(flow, 'draft_status_held');
    if (!heldStatus.success || !heldStatus.selectedLectureIds.includes(Number(lectureId))) {
        releaseLecture(lectureId, flow);
        cancelRegistration(flow);
        return failJourney(flow, 'draft_status_held', { lectureId });
    }

    const released = releaseLecture(lectureId, flow);
    releaseSuccess.add(released.success, { flow, lectureId });
    if (!released.success) {
        cancelRegistration(flow);
        return failJourney(flow, `release:${released.message || released.status}`, { lectureId });
    }

    const releasedStatus = getDraftStatus(flow, 'draft_status_released');
    const draftEmpty = releasedStatus.success && releasedStatus.selectedLectureIds.length === 0;
    emptyDraftAfterRelease.add(draftEmpty, { flow, lectureId });
    if (!draftEmpty) {
        cancelRegistration(flow);
        return failJourney(flow, 'draft_status_released', { lectureId });
    }

    if (!cancelRegistration(flow)) {
        return failJourney(flow, 'registration_cancel', { lectureId });
    }

    finishJourney(flow, true, { lectureId });
}

function buildScenarios() {
    const scenarios = {};
    addScenario(scenarios, 'browse_users', 'browseJourney', BROWSE_USERS);
    addScenario(scenarios, 'lookup_users', 'lookupJourney', LOOKUP_USERS);
    addScenario(scenarios, 'registration_users', 'registrationAbandonJourney', REGISTRATION_USERS);
    addScenario(scenarios, 'hold_release_users', 'holdReleaseJourney', HOLD_RELEASE_USERS);

    if (Object.keys(scenarios).length === 0) {
        throw new Error('최소 하나의 사용자 시나리오 VU 수가 1 이상이어야 합니다.');
    }

    return scenarios;
}

function addScenario(scenarios, name, execName, vus) {
    if (vus === 0) {
        return;
    }

    scenarios[name] = {
        executor: 'per-vu-iterations',
        exec: execName,
        vus,
        iterations: 1,
        maxDuration: MAX_DURATION
    };
}

function buildThresholds() {
    const thresholds = {
        unexpected_flow_error: ['count==0'],
        server_response_error: ['count==0'],
        'http_req_failed{endpoint:home}': ['rate<0.001'],
        'journey_step_duration{endpoint:home}': ['p(95)<1500', 'p(99)<3000'],
        'journey_step_duration{endpoint:lecture_catalog}': ['p(95)<2000', 'p(99)<4000'],
        'journey_step_duration{endpoint:lookup_search}': ['p(95)<2000', 'p(99)<4000'],
        'journey_step_duration{endpoint:lecture_selection}': ['p(95)<2500', 'p(99)<5000']
    };

    addRateThreshold(thresholds, BROWSE_USERS, 'journey_success{flow:browse}', 'rate>0.995');
    addRateThreshold(thresholds, LOOKUP_USERS, 'journey_success{flow:lookup}', 'rate>0.995');
    addRateThreshold(thresholds, LOOKUP_USERS, 'lookup_miss_success', 'rate>0.995');
    addRateThreshold(
        thresholds,
        REGISTRATION_USERS,
        'journey_success{flow:registration_abandon}',
        'rate>0.995'
    );
    addRateThreshold(thresholds, REGISTRATION_USERS, 'registration_abandon_success', 'rate>0.995');
    addRateThreshold(thresholds, HOLD_RELEASE_USERS, 'journey_success{flow:hold_release}', 'rate>0.995');
    addRateThreshold(thresholds, HOLD_RELEASE_USERS, 'hold_success', 'rate>0.995');
    addRateThreshold(thresholds, HOLD_RELEASE_USERS, 'release_success', 'rate>0.995');
    addRateThreshold(thresholds, HOLD_RELEASE_USERS, 'empty_draft_after_release', 'rate>0.995');

    return thresholds;
}

function addRateThreshold(thresholds, users, metric, expression) {
    if (users > 0) {
        thresholds[metric] = [expression];
    }
}

function prepareRegistration(applicant, flow) {
    const home = getPage('/home', 'home', flow);
    if (!isSuccessfulPage(home, isHomePage)) {
        return false;
    }

    const register = getPage('/register', 'register_page', flow);
    if (!isRegisterPageReady(register)) {
        return false;
    }

    const registration = timedRequest('registration_form', flow, () => http.post(
        `${BASE_URL}/lecture`,
        {
            name: applicant.name,
            participantTypeId: PARTICIPANT_TYPE_ID,
            churchId: CHURCH_ID,
            phoneNumber: applicant.phoneNumber
        },
        noBodyParams('registration_form', flow, http.expectedStatuses(302))
    ));
    if (!isRedirectTo(registration, '/lecture')) {
        return false;
    }

    const lecture = getPage('/lecture', 'lecture_selection', flow);
    return lecture.status === 200 && lecture.body.includes('data-draft-panel');
}

function holdLecture(lectureId, flow) {
    const response = timedRequest('hold', flow, () => http.post(
        `${BASE_URL}/lectures/apply`,
        { lectureId },
        ajaxParams('hold', flow, http.expectedStatuses(200, 400, 409), lectureId)
    ));
    const body = parseJson(response);

    return {
        success: response.status === 200 && body?.success === true && body?.selected === true,
        status: response.status,
        message: body?.message || ''
    };
}

function releaseLecture(lectureId, flow) {
    const response = timedRequest('release', flow, () => http.post(
        `${BASE_URL}/lectures/cancel`,
        { lectureId },
        ajaxParams('release', flow, http.expectedStatuses(200, 400, 409), lectureId)
    ));
    const body = parseJson(response);

    return {
        success: response.status === 200 && body?.success === true && body?.selected === false,
        status: response.status,
        message: body?.message || ''
    };
}

function getDraftStatus(flow, endpoint) {
    const response = timedRequest(endpoint, flow, () => http.get(
        `${BASE_URL}/lectures/draft-status`,
        textParams(endpoint, flow, http.expectedStatuses(200, 400, 401, 409))
    ));
    const body = parseJson(response);
    const selectedLectures = Array.isArray(body?.selectedLectures) ? body.selectedLectures : [];

    return {
        success: response.status === 200 && body?.success === true,
        selectedLectureIds: selectedLectures.map(lecture => Number(lecture.lectureId))
    };
}

function cancelRegistration(flow) {
    const response = timedRequest('registration_cancel', flow, () => http.post(
        `${BASE_URL}/registration/cancel`,
        null,
        noBodyParams('registration_cancel', flow, http.expectedStatuses(302))
    ));

    return isRedirectTo(response, '/home');
}

function verifyWriteFlowAvailable() {
    const flow = 'preflight';
    const applicant = {
        name: `loadtest-${RUN_ID}-write-preflight`,
        phoneNumber: '01099999999'
    };
    const registration = http.post(
        `${BASE_URL}/lecture`,
        {
            name: applicant.name,
            participantTypeId: PARTICIPANT_TYPE_ID,
            churchId: CHURCH_ID,
            phoneNumber: applicant.phoneNumber
        },
        noBodyParams('preflight_registration', flow, http.expectedStatuses(302))
    );

    if (!isRedirectTo(registration, '/lecture')) {
        throw new Error('쓰기 사전 점검에서 신청 세션을 시작하지 못했습니다.');
    }

    const lectureId = HOLD_LECTURE_IDS[0];
    const held = holdLecture(lectureId, flow);
    if (!held.success) {
        cancelRegistration(flow);
        throw new Error(`쓰기 사전 점검 점유 실패: HTTP ${held.status} ${held.message}`);
    }

    const released = releaseLecture(lectureId, flow);
    cancelRegistration(flow);
    if (!released.success) {
        throw new Error(`쓰기 사전 점검 해제 실패: HTTP ${released.status} ${released.message}`);
    }
}

function getPage(path, endpoint, flow) {
    return timedRequest(endpoint, flow, () => http.get(
        withRunMarker(path, flow, endpoint),
        textParams(endpoint, flow, http.expectedStatuses(200))
    ));
}

function timedRequest(endpoint, flow, request) {
    const response = request();
    stepDuration.add(response.timings.duration, { endpoint, flow });

    if (response.status === 0 || response.status >= 500) {
        const status = String(response.status || 'network');
        serverResponseError.add(1, { endpoint, flow, status });
        console.error(JSON.stringify({
            type: 'server_response_error',
            flow,
            endpoint,
            status,
            error: response.error || '',
            errorCode: response.error_code || 0,
            vu: exec.vu.idInTest
        }));
    }

    return response;
}

function textParams(endpoint, flow, responseCallback) {
    return {
        redirects: 0,
        responseType: 'text',
        responseCallback,
        headers: commonHeaders(),
        tags: { endpoint, flow }
    };
}

function noBodyParams(endpoint, flow, responseCallback) {
    return {
        redirects: 0,
        responseType: 'none',
        responseCallback,
        headers: commonHeaders(),
        tags: { endpoint, flow }
    };
}

function ajaxParams(endpoint, flow, responseCallback, lectureId) {
    return {
        redirects: 0,
        responseType: 'text',
        responseCallback,
        headers: {
            ...commonHeaders(),
            'X-Requested-With': 'XMLHttpRequest'
        },
        tags: { endpoint, flow, lectureId }
    };
}

function commonHeaders() {
    return { 'X-Load-Test-Run': RUN_ID };
}

function withRunMarker(path, flow, endpoint) {
    const separator = path.includes('?') ? '&' : '?';
    return `${BASE_URL}${path}${separator}loadTest=${encodeURIComponent(
        `${RUN_ID}-${flow}-${endpoint}-${exec.vu.idInTest}-${Date.now()}`
    )}`;
}

function createApplicant(kind) {
    const sequence = PHONE_SEQUENCE_START + exec.vu.idInTest;
    if (sequence > 99999999) {
        throw new Error('PHONE_SEQUENCE_START와 VU 수의 합은 8자리를 넘을 수 없습니다.');
    }

    return {
        name: `loadtest-${RUN_ID}-${kind}-${exec.vu.idInTest}`,
        phoneNumber: `010${String(sequence).padStart(8, '0')}`
    };
}

function isHomePage(body) {
    return body.includes('<title>진로특강 신청</title>');
}

function isOpenHome(body) {
    return isHomePage(body)
        && body.includes('현재 특강 신청을 받고 있습니다.')
        && body.includes('href="/register"');
}

function isLectureCatalogPage(body) {
    return body.includes('<title>전체 강좌 보기</title>')
        && body.includes('lectureEntity-card');
}

function isLookupPage(body) {
    return body.includes('신청 내역 조회') && body.includes('action="/lookup"');
}

function isRegisterPageReady(response) {
    return response.status === 200
        && response.body.includes(`value="${PARTICIPANT_TYPE_ID}"`)
        && response.body.includes(`data-church-id="${CHURCH_ID}"`);
}

function isSuccessfulPage(response, bodyPredicate) {
    return response.status === 200 && bodyPredicate(response.body);
}

function isRedirectTo(response, path) {
    const location = response.headers.Location || '';
    return response.status === 302 && location.endsWith(path);
}

function parseJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function think() {
    if (THINK_TIME_MAX_MS === 0) {
        return;
    }
    sleep((Math.random() * THINK_TIME_MAX_MS) / 1000);
}

function failJourney(flow, phase, tags = {}) {
    unexpectedFlowError.add(1, { flow, phase, ...tags });
    console.error(JSON.stringify({
        type: 'journey_failure',
        flow,
        phase,
        vu: exec.vu.idInTest,
        ...tags
    }));
    finishJourney(flow, false, tags);
}

function finishJourney(flow, successful, tags = {}) {
    journeySuccess.add(successful, { flow, ...tags });
    check(successful, {
        [`${flow} 사용자 흐름 성공`]: value => value === true
    });
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
    const rawValue = __ENV[name] || String(defaultValue);
    const value = Number.parseInt(rawValue, 10);

    if (!/^\d+$/.test(rawValue) || !Number.isInteger(value) || value <= 0) {
        throw new Error(`${name}은 양의 정수여야 합니다.`);
    }

    return value;
}

function nonNegativeIntegerEnv(name, defaultValue) {
    const rawValue = __ENV[name] || String(defaultValue);
    const value = Number.parseInt(rawValue, 10);

    if (!/^\d+$/.test(rawValue) || !Number.isInteger(value) || value < 0) {
        throw new Error(`${name}은 0 이상의 정수여야 합니다.`);
    }

    return value;
}

function parsePositiveIntegerList(rawValue) {
    if (!rawValue.trim()) {
        return [];
    }

    const values = rawValue.split(',').map(value => value.trim());
    if (values.some(value => !/^\d+$/.test(value) || Number.parseInt(value, 10) <= 0)) {
        throw new Error('HOLD_LECTURE_IDS는 쉼표로 구분한 양의 정수여야 합니다.');
    }

    return [...new Set(values.map(value => Number.parseInt(value, 10)))];
}

function sanitizeRunId(value) {
    const sanitized = value.replace(/[^a-zA-Z0-9_-]/g, '');
    return sanitized || 'journey';
}
