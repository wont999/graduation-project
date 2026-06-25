import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '1m', target: 20 },
        { duration: '28m', target: 20 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';
const CLIENT_SECRET = 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs';

let currentToken = null;
let tokenExpiry = 0;

function refreshToken() {
    const res = http.post(KEYCLOAK_URL, {
        grant_type: 'password',
        client_id: 'gateway-client',
        client_secret: CLIENT_SECRET,
        username: 'testuser',
        password: 'testpass',
    }, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    if (res.status === 200) {
        currentToken = res.json('access_token');
        tokenExpiry = Date.now() + 240000; // 4 минуты
        return true;
    }
    console.error('Token refresh failed:', res.status, res.body);
    return false;
}

function getToken() {
    if (!currentToken || Date.now() > tokenExpiry) {
        refreshToken();
    }
    return currentToken;
}

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

    if (!success) {
        errorRate.add(1);
        console.log(`ERROR status=${res.status} body=${res.body ? res.body.substring(0, 200) : 'null'}`);
    } else {
        errorRate.add(0);
        latency.add(res.timings.duration);
    }
    return bodyObj;
}

export function setup() {
    if (!refreshToken()) {
        return null;
    }
    return { token: currentToken };
}

const SCRIPTS = [
    // 40% — чтение + подсчёт (простая агрегация)
    {
        weight: 40,
        script: `(function(){
            var raw = DB.table('products').findAll();
            var items = __toArr.toArray(raw);
            var sum = 0;
            for (var i = 0; i < items.length; i++) { sum += items[i].price; }
            return {count: items.length, avgPrice: items.length > 0 ? Math.round(sum / items.length) : 0};
        })()`
    },

    // 25% — фильтрация + сортировка
    {
        weight: 25,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: 'between', from: 50, to: 500}, __limit: 100});
            var items = __toArr.toArray(raw);
            items.sort(function(a, b) { return a.price - b.price; });
            return {count: items.length, cheapest: items[0] ? items[0].price : 0, mostExpensive: items[items.length-1] ? items[items.length-1].price : 0};
        })()`
    },

    // 20% — цепочка: поиск + обновление
    {
        weight: 20,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: '<', value: 50}, __limit: 10});
            var items = __toArr.toArray(raw);
            var updated = 0;
            for (var i = 0; i < Math.min(items.length, 2); i++) {
                DB.table('products').update(items[i].id, {price: Math.round(items[i].price * 1.1)});
                updated++;
            }
            return {found: items.length, updated: updated};
        })()`
    },

    // 15% — создание + чтение + удаление
    {
        weight: 15,
        script: `(function(){
            var created = DB.table('products').create({name: 'stability-' + Date.now(), price: Math.floor(Math.random() * 300) + 1});
            var found = DB.table('products').findById(created.id);
            DB.table('products').delete(created.id);
            return {createdId: created.id, foundName: found ? found.name : null};
        })()`
    },
];

export default function (data) {
    if (!data) return;

    const token = getToken();
    if (!token) return;

    const totalWeight = SCRIPTS.reduce((sum, s) => sum + s.weight, 0);
    let rand = Math.random() * totalWeight;
    let script = SCRIPTS[0].script;
    for (const s of SCRIPTS) {
        rand -= s.weight;
        if (rand <= 0) { script = s.script; break; }
    }

    executeScript(script, token);
    sleep(0.5);
}

export function handleSummary(data) {
    return {
        'results/stability-30min-summary.json': JSON.stringify(data, null, 2),
    };
}
