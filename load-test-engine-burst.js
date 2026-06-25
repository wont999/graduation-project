import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const errors = new Counter('errors');
const latency = new Trend('engine_latency', true);

export const options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',
            vus: 200,
            iterations: 1,
            maxDuration: '2m',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const CLIENT_SECRET = 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs';

const SCRIPT = `DB.table('products').where({ price: { op: 'between', from: 100, to: 500 } }).limit(500)`;

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
        timeout: '60s',
    });

    const ok = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    if (ok) {
        latency.add(res.timings.duration);
    } else {
        errors.add(1);
    }
}
