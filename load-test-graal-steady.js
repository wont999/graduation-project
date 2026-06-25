import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const latency = new Trend('latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const RATE = parseInt(__ENV.RATE || '200');
const SCENARIO = __ENV.SCENARIO || 'cpu-heavy';
const TEST_ID = __ENV.TEST_ID || 'default';

export const options = {
    setupTimeout: '120s',
    scenarios: {
        steady: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: RATE * 2,
            maxVUs: RATE * 4,
            exec: 'steadyScenario',
            tags: { test_id: TEST_ID, scenario: 'steady', rate: String(RATE) }
        }
    }
};

const SCRIPTS = {
    'cpu-heavy': `
let map = {};
for (let i = 0; i < 5000; i++) {
    map["key_" + i] = { value: i, arr: [1,2,3,4,5] };
}
Object.keys(map).length;`,
    'exec-only': `DB.table('products').findAll({ __limit: 100 })`
};

function executeScript(script, token) {
    const payload = JSON.stringify({
        clientType: "blockly-executor",
        procedureName: "executeBlocklyScript",
        parameters: { script, parameters: {} }
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        timeout: '120s',
        tags: { test_id: TEST_ID, scenario: 'steady', rate: String(RATE) }
    });
    const duration = Date.now() - start;

    let body = null;
    try { body = res.json(); } catch (_) {}

    check(res, { 'status is 200': r => r.status === 200 });

    return { success: body?.success === true, duration };
}

export function setup() {
    const res = http.post(KEYCLOAK_URL,
        {
            grant_type: 'password',
            client_id: 'gateway-client',
            client_secret: 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs',
            username: 'testuser',
            password: 'testpass'
        },
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    );
    return { token: res.json('access_token') };
}

export function steadyScenario(data) {
    const script = SCRIPTS[SCENARIO] || SCRIPTS['cpu-heavy'];
    const result = executeScript(script, data.token);
    latency.add(result.duration);
}
