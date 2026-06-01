import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '60s', target: 200 },
        { duration: '60s', target: 500 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.5'],
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
        console.log(`FAIL vu=${__VU} status=${res.status} body=${(res.body || '').substring(0, 200)}`);
    } else {
        errorRate.add(0);
        latency.add(duration);
    }

    return bodyObj;
}

export function setup() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: 'sUTYSTdef4d9h8STvcbAJkbwtIClrLe7',
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
    console.log('Auth successful');
    return { token };
}

const SCRIPTS = [
    // 30% reads: simple findAll
    { weight: 30, script: `var r = DB.table('products').findAll(100); 'ok'` },

    // 20% reads: JOIN aggregation
    { weight: 20, script: `var r = DB.query('SELECT status, COUNT(*) as count, AVG(total) as avg_total FROM tenant_appliner.orders GROUP BY status'); 'ok'` },

    // 15% reads: complex JOIN
    { weight: 15, script: `var r = DB.query('SELECT o.id, o.user_id, o.total, COUNT(oi.id) as items FROM tenant_appliner.orders o LEFT JOIN tenant_appliner.order_items oi ON o.id = oi.order_id GROUP BY o.id, o.user_id, o.total ORDER BY o.total DESC LIMIT 10'); 'ok'` },

    // 15% writes: create product
    { weight: 15, script: `var r = DB.table('products').create({name: 'test-' + Date.now() + '-' + ${__VU}, price: Math.floor(Math.random() * 500) + 1}); 'ok'` },

    // 10% writes: create order + items
    { weight: 10, script: `
        var order = DB.table('orders').create({user_id: 'vu-${__VU}', total: 0, status: 'pending'});
        var orderId = order.get('id');
        var total = 0;
        for (var i = 0; i < 2; i++) {
            var price = Math.floor(Math.random() * 100) + 10;
            DB.table('order_items').create({order_id: orderId, product_id: 1, quantity: 1, price: price});
            total += price;
        }
        DB.table('orders').update(orderId, {total: total, status: 'completed'});
        'order_' + orderId
    ` },

    // 10% writes: update product
    { weight: 10, script: `var r = DB.table('products').update(Math.floor(Math.random() * 142000) + 1, {price: Math.floor(Math.random() * 500) + 1}); 'ok'` },
];

function pickScript() {
    const totalWeight = SCRIPTS.reduce((sum, s) => sum + s.weight, 0);
    let random = Math.random() * totalWeight;
    for (const item of SCRIPTS) {
        random -= item.weight;
        if (random <= 0) return item.script;
    }
    return SCRIPTS[0].script;
}

export default function (data) {
    if (!data || !data.token) {
        return;
    }

    const script = pickScript();
    executeScript(script, data.token);

    sleep(0.1);
}
