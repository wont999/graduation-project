import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { check } from 'k6';

const latency = new Trend('graalvm_latency');

export const options = {
    scenarios: {
        fixed_100: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 100,
            maxDuration: '120s',
        },
    },
    thresholds: {
        'graalvm_latency': ['p(95) < 5000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';

export function setup() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: 'sUTYSTdef4d9h8STvcbAJkbwtIClrLe7',
        username: 'testuser',
        password: 'testpass',
    }, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
    return { token: res.json('access_token') };
}

export default function (data) {
    const payload = JSON.stringify({
        clientType: 'blockly-executor',
        procedureName: 'executeBlocklyScript',
        parameters: { script: `DB.table('products').findAll()`, parameters: {} },
    });

    const res = http.post(`${BASE_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`,
            'X-User-Id': 'testuser',
            'X-Organization-Id': 'appliner',
        },
    });
    latency.add(res.timings.duration);
    check(res, { 'status 200': r => r.status === 200 });
}