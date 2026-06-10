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
    // S1: Чтение + JS-логика: фильтрация, сортировка, агрегация
    {
        weight: 15,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: 'between', from: 100, to: 500}, __limit: 100});
            var products = __toArr.toArray(raw);
            var sum = 0;
            for (var i = 0; i < products.length; i++) { sum += products[i].price; }
            return {count: products.length, avgPrice: products.length > 0 ? Math.round(sum / products.length) : 0};
        })()`
    },

    // S2: Цепочка запросов: поиск + обновление (read-modify-write)
    {
        weight: 10,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: '>', value: 500}, __limit: 50});
            var items = __toArr.toArray(raw);
            var updated = 0;
            for (var i = 0; i < Math.min(items.length, 3); i++) {
                DB.table('products').update(items[i].id, {price: Math.round(items[i].price * 0.9)});
                updated++;
            }
            return {scanned: items.length, updated: updated};
        })()`
    },

    // S3: Валидация + условное создание (3 запроса)
    {
        weight: 10,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: '>', value: 1000}, __limit: 100});
            var expensive = __toArr.toArray(raw);
            if (expensive.length > 0) {
                var sum = 0;
                for (var i = 0; i < expensive.length; i++) { sum += expensive[i].price; }
                DB.table('products').create({name: 'alert-high-price', price: expensive.length});
                return {flagged: expensive.length, avgPrice: Math.round(sum / expensive.length)};
            }
            return {flagged: 0, avgPrice: 0};
        })()`
    },

    // S4: SQL + DB API комбинация: подсчёт записей + запрос с GROUP BY
    {
        weight: 15,
        script: `(function(){
            var total = DB.table('products').count();
            var raw = DB.query('SELECT CASE WHEN price < 100 THEN \'cheap\' WHEN price < 500 THEN \'mid\' ELSE \'expensive\' END as tier, COUNT(*) as cnt FROM tenant_appliner.products GROUP BY tier ORDER BY cnt DESC');
            var groups = __toArr.toArray(raw);
            return {total: total, tiers: groups};
        })()`
    },

    // S5: Поиск extremes + обновление по условию
    {
        weight: 10,
        script: `(function(){
            var rawCheap = DB.table('products').where({price: {op: '>', value: 0}, __limit: 100});
            var rawExp = DB.table('products').where({price: {op: '>', value: 500}, __limit: 100});
            var cheapest = __toArr.toArray(rawCheap);
            var mostExpensive = __toArr.toArray(rawExp);
            var min = 999999, max = 0, minId = 0;
            for (var i = 0; i < cheapest.length; i++) {
                if (cheapest[i].price < min) { min = cheapest[i].price; minId = cheapest[i].id; }
            }
            for (var i = 0; i < mostExpensive.length; i++) {
                if (mostExpensive[i].price > max) { max = mostExpensive[i].price; }
            }
            if (minId > 0) { DB.table('products').update(minId, {price: min + 1}); }
            return {min: min, max: max, count: cheapest.length + mostExpensive.length};
        })()`
    },

    // S6: Трансформация данных: обновление пакета записей
    {
        weight: 10,
        script: `(function(){
            var raw = DB.table('products').where({price: {op: '<', value: 50}, __limit: 50});
            var items = __toArr.toArray(raw);
            var updated = 0;
            for (var i = 0; i < items.length; i++) {
                DB.table('products').update(items[i].id, {price: Math.round(items[i].price * 1.1)});
                updated++;
            }
            return {updated: updated};
        })()`
    },

    // S7: Поиск по шаблону + сортировка + подсчёт
    {
        weight: 10,
        script: `(function(){
            var raw = DB.table('products').where({name: {op: 'like', value: '%vu-%'}, __limit: 100});
            var items = __toArr.toArray(raw);
            var sum = 0;
            for (var i = 0; i < items.length; i++) { sum += items[i].price; }
            return {found: items.length, totalValue: sum, avg: items.length > 0 ? Math.round(sum / items.length) : 0};
        })()`
    },

    // S8: IN + BETWEEN: комбинированные условия
    {
        weight: 10,
        script: `(function(){
            var rawMid = DB.table('products').where({price: {op: 'between', from: 100, to: 500}, __limit: 100});
            var rawCheap = DB.table('products').where({price: {op: '<', value: 50}, __limit: 100});
            var mid = __toArr.toArray(rawMid);
            var cheap = __toArr.toArray(rawCheap);
            var sumMid = 0, sumCheap = 0;
            for (var i = 0; i < mid.length; i++) { sumMid += mid[i].price; }
            for (var i = 0; i < cheap.length; i++) { sumCheap += cheap[i].price; }
            return {mid: {count: mid.length, sum: sumMid}, cheap: {count: cheap.length, sum: sumCheap}};
        })()`
    },

    // S9: SQL + JS-обработка: MIN/MAX/AVG + распределение по бакетам
    {
        weight: 10,
        script: `(function(){
            var rawStats = DB.query('SELECT MIN(price) as min_price, MAX(price) as max_price, AVG(price)::int as avg_price FROM tenant_appliner.products WHERE price > 0');
            var statsArr = __toArr.toArray(rawStats);
            var s = statsArr[0];
            var range = s.max_price - s.min_price;
            var rawBuckets = DB.query('SELECT (price / 100) * 100 as bucket, COUNT(*) as cnt FROM tenant_appliner.products WHERE price > 0 GROUP BY bucket ORDER BY bucket');
            var buckets = __toArr.toArray(rawBuckets);
            return {range: range, stats: s, distribution: buckets};
        })()`
    },

    // S10: Нагрузочный: 5 операций подряд (read+read+write+write+read)
    {
        weight: 10,
        script: `(function(){
            var rawAll = DB.table('products').findAll();
            var all = __toArr.toArray(rawAll);
            var total = DB.table('products').count();
            DB.table('products').create({name: 'load-vu-' + __VU + '-' + Date.now(), price: Math.floor(Math.random() * 500) + 1});
            var rawLast = DB.table('products').where({name: {op: 'like', value: '%load-vu-%'}, __limit: 5});
            var last = __toArr.toArray(rawLast);
            if (last.length > 0) {
                DB.table('products').delete(last[last.length - 1].id);
            }
            return {page: all.length, total: total, created: 1, deleted: last.length > 0 ? 1 : 0};
        })()`
    },
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
