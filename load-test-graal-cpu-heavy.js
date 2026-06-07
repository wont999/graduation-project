import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const cpuLatency = new Trend('cpu_latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const VU_COUNT = parseInt(__ENV.VU_COUNT || '200');

export const options = {
    setupTimeout: '120s',
    scenarios: {
        cpu_heavy: {
            executor: 'constant-arrival-rate',
            rate: VU_COUNT,
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: VU_COUNT,
            maxVUs: VU_COUNT,
            exec: 'cpuScenario',
        }
    }
};

const CPU_SCRIPT = `
let sum = 0;
for (let i = 0; i < 10000; i++) {
    sum += i * Math.random();
}
return sum;
`;

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

export function cpuScenario(data) {
    const result = executeScript(CPU_SCRIPT, data.token);
    cpuLatency.add(result.duration);
}
