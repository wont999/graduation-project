import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const updateLatency = new Trend('update_latency');

export const options = {
    stages: [
        { duration: '3s', target: 10 },
        { duration: '10s', target: 50 },
        { duration: '5s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        http_req_failed: ['rate<0.05'],
        errors: ['rate<0.05'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8180/routing';
const KEYCLOAK_URL = 'http://host.docker.internal:8080/realms/appliner/protocol/openid-connect/token';

function executeScript(script, token) {
    const payload = JSON.stringify({
        clientType: "blockly-executor",
        procedureName: "executeBlocklyScript",
        parameters: {
            script: script,
            parameters: {}
        }
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
        timeout: '30s',
    });
    const duration = Date.now() - start;

    let bodyObj = null;
    try { bodyObj = res.json(); } catch (e) { /* not json */ }

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'success is true': () => bodyObj && bodyObj.success === true,
    });

    if (!success) {
        errorRate.add(1);
        console.log(`FAIL status=${res.status} body=${(res.body || '').substring(0, 150)}`);
    } else {
        errorRate.add(0);
        updateLatency.add(duration);
    }

    return bodyObj;
}

export function setup() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: 'GzDN4TtTvuZTxrQqr1YRrp1g5Rhem8A7',
        username: 'testuser',
        password: 'testpass',
    }, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    if (res.status !== 200) {
        console.error('Keycloak auth failed:', res.status, res.body);
        return null;
    }

    const token = res.json('access_token');

    // Создаём 50 продуктов
    const products = [];
    for (let i = 0; i < 50; i++) {
        const createRes = executeScript(`DB.table('products').create({name: 'k6-${i}', price: 100})`, token);
        if (createRes && createRes.result && createRes.result.id) {
            products.push(createRes.result.id);
        }
    }

    console.log(`Created ${products.length} products`);
    return { token, products };
}

export default function (data) {
    if (!data || !data.token || !data.products || data.products.length === 0) {
        console.error('No data available');
        return;
    }

    // Каждый VU обновляет "свой" продукт (по модулю)
    const productId = data.products[__VU % data.products.length];
    const randomPrice = Math.floor(Math.random() * 1000) + 1;

    executeScript(`DB.table('products').update(${productId}, {price: ${randomPrice}})`, data.token);
    sleep(Math.random() * 0.5 + 0.1);
}