import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const execCount = new Counter('exec_requests');
const dbReadCount = new Counter('db_read_requests');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const VU_COUNT = parseInt(__ENV.VU_COUNT || '200');

export const options = {
    setupTimeout: '120s',
    scenarios: {
        mixed: {
            executor: 'constant-arrival-rate',
            rate: VU_COUNT,
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: VU_COUNT,
            maxVUs: VU_COUNT,
            exec: 'mixedScenario',
        }
    }
};

const EXEC_SCRIPT = `DB.table('products').findAll({ __limit: 100 })`;
const DB_READ_SCRIPT = `DB.table('products').findAll({ __limit: 50 })`;

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
        timeout: '120s'
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

export function mixedScenario(data) {
    const rand = Math.random();
    if (rand < 0.8) {
        const result = executeScript(EXEC_SCRIPT, data.token);
        execCount.add(1);
    } else {
        const result = executeScript(DB_READ_SCRIPT, data.token);
        dbReadCount.add(1);
    }
}
