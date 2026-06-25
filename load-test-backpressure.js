import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const errors = new Counter('errors');
const rejected = new Counter('rejected');
const accepted = new Counter('accepted');
const latency = new Trend('request_latency');

export const options = {
    scenarios: {
        ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 300 },
                { duration: '30s', target: 300 },
                { duration: '5s', target: 0 },
            ],
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const CLIENT_SECRET = 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs';

const SCRIPT = `DB.query('SELECT pg_sleep(3), id FROM tenant_appliner.products LIMIT 10')`;

export function setup() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: CLIENT_SECRET,
        username: 'testuser',
        password: 'testpass',
    }, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    if (res.status !== 200) {
        console.error('Keycloak auth failed:', res.status, res.body);
        return null;
    }

    return { token: res.json('access_token') };
}

export default function (data) {
    if (!data || !data.token) return;

    const payload = JSON.stringify({
        clientType: 'blockly-executor',
        procedureName: 'executeBlocklyScript',
        parameters: { script: SCRIPT, parameters: {} },
    });

    const res = http.post(`${BASE_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`,
        },
        timeout: '30s',
    });

    let bodyObj = null;
    try { bodyObj = res.json(); } catch (e) {}

    if (res.status === 0) {
        errors.add(1);
    } else if (bodyObj && bodyObj.errorMessage && bodyObj.errorMessage.includes('capacity')) {
        rejected.add(1);
    } else if (res.status === 200 && bodyObj && bodyObj.success) {
        accepted.add(1);
        latency.add(res.timings.duration);
    } else {
        errors.add(1);
    }
}
