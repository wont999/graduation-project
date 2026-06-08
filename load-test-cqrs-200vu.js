import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');
const readLatency = new Trend('read_latency');
const writeLatency = new Trend('write_latency');
const readCounter = new Counter('read_ops');
const writeCounter = new Counter('write_ops');

const VU = 200;

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '1m', target: VU },
        { duration: '3m', target: VU },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.15'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';

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
        timeout: '120s',
    });
    const duration = Date.now() - start;

    let bodyObj = null;
    try { bodyObj = res.json(); } catch (e) {}

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'success is true': () => bodyObj && bodyObj.success === true,
    });

    if (res.status === 0) {
        console.log(`TIMEOUT vu=${__VU}`);
    } else if (!success) {
        errorRate.add(1);
    } else {
        errorRate.add(0);
        latency.add(duration);
    }

    return { bodyObj, success, duration };
}

export function setup() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs',
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
    console.log('Auth successful, VU: ' + VU + ', scenario: 50/50 findAll + create/update, 10K rows');
    return { token };
}

export default function (data) {
    if (!data || !data.token) {
        return;
    }

    const rand = Math.random();

    if (rand < 0.5) {
        const result = executeScript(`DB.table('products').findAll()`, data.token);
        if (result.success) {
            readCounter.add(1);
            readLatency.add(result.duration);
        }
    } else {
        const price = Math.floor(Math.random() * 1000) + 1;
        const writeRand = Math.random();
        let result;
        if (writeRand < 0.5) {
            result = executeScript(`DB.table('products').create({name: 'vu-${__VU}-${__ITER}', price: ${price}})`, data.token);
        } else {
            const id = Math.floor(Math.random() * 10000) + 1;
            result = executeScript(`DB.table('products').update(${id}, {price: ${price}})`, data.token);
        }
        if (result.success) {
            writeCounter.add(1);
            writeLatency.add(result.duration);
        }
    }

    sleep(0.1);
}
