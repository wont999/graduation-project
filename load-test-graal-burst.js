import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const latency = new Trend('latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const VU_COUNT = parseInt(__ENV.VU_COUNT || '200');
const SCENARIO = __ENV.SCENARIO || 'exec-only';

export const options = {
    setupTimeout: '120s',
    scenarios: {
        burst: {
            executor: 'ramping-vus',
            stages: [
                { duration: '5s', target: VU_COUNT },
                { duration: '10s', target: VU_COUNT },
                { duration: '5s', target: 0 }
            ],
            gracefulRampDown: '0s',
            exec: 'burstScenario'
        }
    }
};

const SCRIPTS = {
    'exec-only': `DB.table('products').findAll({ __limit: 100 })`,
    'cpu-heavy': `(function(){let r=0;for(let i=0;i<100000;i++){r+=Math.sqrt(i)*Math.sin(i);}return r;})()`,
    'mixed': null
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

export function burstScenario(data) {
    let script;
    if (SCENARIO === 'mixed') {
        script = Math.random() < 0.8
            ? SCRIPTS['exec-only']
            : `DB.table('products').findAll({ __limit: 50 })`;
    } else {
        script = SCRIPTS[SCENARIO];
    }

    const result = executeScript(script, data.token);
    latency.add(result.duration);
}
