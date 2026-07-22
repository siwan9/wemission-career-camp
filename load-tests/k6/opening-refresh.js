import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const REFRESH_VUS = integerEnv('REFRESH_VUS', 3000);
const PRE_OPEN_OFFSET_MS = integerEnv('PRE_OPEN_OFFSET_MS', 500);
const POST_OPEN_OFFSET_MS = integerEnv('POST_OPEN_OFFSET_MS', 1500);
const OBSERVER_INTERVAL_MS = integerEnv('OBSERVER_INTERVAL_MS', 100);
const OBSERVER_TIMEOUT_MS = integerEnv('OBSERVER_TIMEOUT_MS', 5000);
const INCLUDE_STATIC_ASSETS = (__ENV.INCLUDE_STATIC_ASSETS || 'false') === 'true';
const RUN_ID = __ENV.RUN_ID || 'opening';

const homeSuccess = new Rate('home_success');
const postOpenVisible = new Rate('post_open_visible');
const transitionOnTime = new Rate('open_transition_on_time');
const homeDuration = new Trend('home_duration', true);
const transitionDelay = new Trend('open_transition_delay', true);

export const options = {
    discardResponseBodies: true,
    scenarios: {
        refresh_burst: {
            executor: 'per-vu-iterations',
            exec: 'refreshBurst',
            vus: REFRESH_VUS,
            iterations: 1,
            maxDuration: '2m'
        },
        opening_observer: {
            executor: 'per-vu-iterations',
            exec: 'observeOpening',
            vus: 1,
            iterations: 1,
            maxDuration: '2m'
        }
    },
    thresholds: {
        'home_success': ['rate>0.999'],
        'post_open_visible': ['rate==1'],
        'open_transition_on_time': ['rate==1'],
        'home_duration{phase:post_open}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed{endpoint:home}': ['rate<0.001']
    }
};

export function setup() {
    requireConfirmation('opening-refresh');
    requireValue('BASE_URL', BASE_URL);
    const openAtMs = parseFutureInstant('OPEN_AT', __ENV.OPEN_AT);

    if (openAtMs - Date.now() < 30000) {
        throw new Error('OPEN_AT은 실행 시각보다 최소 30초 이후로 지정해야 3,000 VU를 준비할 수 있습니다.');
    }

    const response = http.get(`${BASE_URL}/home`, {
        redirects: 0,
        responseType: 'text',
        tags: { endpoint: 'preflight' }
    });

    if (response.status !== 200) {
        throw new Error(`사전 점검 GET /home 실패: HTTP ${response.status}`);
    }

    return { openAtMs };
}

export function refreshBurst(data) {
    waitUntil(data.openAtMs - PRE_OPEN_OFFSET_MS);
    requestHome('pre_open', false);

    if (INCLUDE_STATIC_ASSETS) {
        requestStaticAssets();
    }

    waitUntil(data.openAtMs + POST_OPEN_OFFSET_MS);
    requestHome('post_open', true);
}

export function observeOpening(data) {
    waitUntil(data.openAtMs - PRE_OPEN_OFFSET_MS);
    const deadline = data.openAtMs + OBSERVER_TIMEOUT_MS;

    while (Date.now() <= deadline) {
        const response = http.get(
            `${BASE_URL}/home?loadTest=${encodeURIComponent(RUN_ID)}-observer-${Date.now()}`,
            {
                redirects: 0,
                responseType: 'text',
                tags: { endpoint: 'home_observer' }
            }
        );

        if (response.status === 200 && isRecruitmentOpen(response.body)) {
            const delayMs = Math.max(0, Date.now() - data.openAtMs);
            transitionDelay.add(delayMs);
            transitionOnTime.add(delayMs <= 2000);
            return;
        }

        sleep(OBSERVER_INTERVAL_MS / 1000);
    }

    transitionDelay.add(OBSERVER_TIMEOUT_MS);
    transitionOnTime.add(false);
}

function requestHome(phase, mustBeOpen) {
    const response = http.get(
        `${BASE_URL}/home?loadTest=${encodeURIComponent(RUN_ID)}-${__VU}-${phase}-${Date.now()}`,
        {
            redirects: 0,
            responseType: 'text',
            tags: { endpoint: 'home', phase }
        }
    );
    const successful = response.status === 200
        && response.body.includes('<title>진로특강 신청</title>');

    homeSuccess.add(successful, { phase });
    homeDuration.add(response.timings.duration, { phase });
    check(response, {
        [`${phase}: 홈 HTML 응답 성공`]: () => successful
    });

    if (mustBeOpen) {
        const visible = successful && isRecruitmentOpen(response.body);
        postOpenVisible.add(visible, { phase });
        check(response, {
            '오픈 후 신청 버튼 노출': () => visible
        });
    }
}

function requestStaticAssets() {
    http.batch([
        ['GET', `${BASE_URL}/vendor/bootstrap/bootstrap.min.css`, null, staticParams('bootstrap_css')],
        ['GET', `${BASE_URL}/css/home.css`, null, staticParams('home_css')],
        ['GET', `${BASE_URL}/css/footer.css`, null, staticParams('footer_css')],
        ['GET', `${BASE_URL}/js/home.js`, null, staticParams('home_js')]
    ]);
}

function staticParams(asset) {
    return {
        responseType: 'none',
        tags: { endpoint: 'static', asset }
    };
}

function isRecruitmentOpen(body) {
    return body.includes('현재 특강 신청을 받고 있습니다.')
        && body.includes('href="/register"');
}

function waitUntil(targetMs) {
    while (Date.now() < targetMs) {
        sleep(Math.min(targetMs - Date.now(), 1000) / 1000);
    }
}

function parseFutureInstant(name, value) {
    requireValue(name, value);
    const timestamp = Date.parse(value);

    if (Number.isNaN(timestamp)) {
        throw new Error(`${name}은 ISO-8601 형식이어야 합니다. 예: 2026-07-25T14:00:00+09:00`);
    }

    return timestamp;
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

function integerEnv(name, defaultValue) {
    const value = Number.parseInt(__ENV[name] || String(defaultValue), 10);

    if (!Number.isInteger(value) || value < 0) {
        throw new Error(`${name}은 0 이상의 정수여야 합니다.`);
    }

    return value;
}
