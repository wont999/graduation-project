import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');
const timeouts = new Counter('timeouts');

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '20s', target: 100 },
        { duration: '40s', target: 200 },
        { duration: '40s', target: 400 },
        { duration: '40s', target: 500 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.3'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const CLIENT_SECRET = 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs';

function executeScript(script, token) {
    const payload = JSON.stringify({
        clientType: "blockly-executor",
        procedureName: "executeBlocklyScript",
        parameters: { script: script, parameters: {} },
    });

    const res = http.post(`${BASE_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
        timeout: '30s',
    });

    let bodyObj = null;
    try { bodyObj = res.json(); } catch (e) {}

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'success is true': () => bodyObj && bodyObj.success === true,
    });

    if (res.status === 0) {
        timeouts.add(1);
    } else if (!success) {
        errorRate.add(1);
    } else {
        errorRate.add(0);
        latency.add(res.timings.duration);
    }

    return bodyObj;
}

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

const SCRIPTS = [
    { weight: 30, script: `DB.table('products').findAll()` },
    { weight: 15, script: `DB.query('SELECT * FROM tenant_appliner.products WHERE price BETWEEN 100 AND 500 LIMIT 500')` },
    { weight: 10, script: `DB.table('products').count()` },
    { weight: 15, script: `DB.table('products').create({name: 'vu-${__VU}-${Date.now()}', price: Math.floor(Math.random() * 1000) + 1})` },
    { weight: 15, script: `DB.table('products').update(Math.floor(Math.random() * 10000) + 1, {price: Math.floor(Math.random() * 1000) + 1})` },
    { weight: 10, script: `DB.query('SELECT price, COUNT(*) as cnt FROM tenant_appliner.products GROUP BY price ORDER BY cnt DESC LIMIT 10')` },
    { weight: 5, script: `DB.table('products').delete(Math.floor(Math.random() * 10000) + 1)` },
];

function pickScript() {
    const total = SCRIPTS.reduce((s, x) => s + x.weight, 0);
    let r = Math.random() * total;
    for (const item of SCRIPTS) {
        r -= item.weight;
        if (r <= 0) return item.script;
    }
    return SCRIPTS[0].script;
}

export default function (data) {
    if (!data || !data.token) return;

    executeScript(pickScript(), data.token);
    sleep(0.1);
}
