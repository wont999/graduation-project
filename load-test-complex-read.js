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

const QUERIES = [
    // JOIN + aggregation: top products by order volume
    `DB.query('SELECT p.name, SUM(oi.quantity) as total_sold FROM tenant_appliner.products p JOIN tenant_appliner.order_items oi ON p.id = oi.product_id GROUP BY p.name ORDER BY total_sold DESC LIMIT 10')`,

    // Aggregation: orders by status
    `DB.query('SELECT status, COUNT(*) as count, AVG(total) as avg_total FROM tenant_appliner.orders GROUP BY status')`,

    // Subquery: products above average price
    `DB.table('products').where({})`,

    // JOIN: orders with items count
    `DB.query('SELECT o.id, o.user_id, o.total, o.status, COUNT(oi.id) as item_count FROM tenant_appliner.orders o LEFT JOIN tenant_appliner.order_items oi ON o.id = oi.order_id GROUP BY o.id, o.user_id, o.total, o.status ORDER BY o.total DESC LIMIT 20')`,

    // Aggregation: revenue by category (simulated)
    `DB.query('SELECT c.name, COUNT(oi.id) as items_sold, SUM(oi.price * oi.quantity) as revenue FROM tenant_appliner.categories c LEFT JOIN tenant_appliner.order_items oi ON c.id = (oi.product_id % 5 + 1) GROUP BY c.name ORDER BY revenue DESC')`,

    // Window function: top orders per user
    `DB.query('SELECT user_id, total, status, created_at FROM tenant_appliner.orders ORDER BY total DESC LIMIT 50')`,

    // Complex: products with order stats
    `DB.query('SELECT p.name, p.price, COUNT(oi.id) as order_count, SUM(oi.quantity) as total_qty FROM tenant_appliner.products p LEFT JOIN tenant_appliner.order_items oi ON p.id = oi.product_id WHERE p.price > 100 GROUP BY p.name, p.price HAVING COUNT(oi.id) > 0 ORDER BY total_qty DESC LIMIT 25')`,

    // Simple findAll with limit
    `DB.table('products').findAll(50)`,
];

export default function (data) {
    if (!data || !data.token) {
        return;
    }

    const query = QUERIES[__VU % QUERIES.length];
    executeScript(query, data.token);

    sleep(0.1);
}
