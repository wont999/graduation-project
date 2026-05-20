import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');
const timeouts = new Counter('timeouts');

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '20s', target: 100 },     // разогрев
        { duration: '40s', target: 300 },     // 300 VU
        { duration: '40s', target: 500 },     // 500 VU
        { duration: '40s', target: 800 },     // 800 VU - ищем предел
        { duration: '20s', target: 0 },       // сброс
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
    timeout: '60s',
  });
  const duration = Date.now() - start;

  let bodyObj = null;
  try { bodyObj = res.json(); } catch (e) {}

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'success is true': () => bodyObj && bodyObj.success === true,
  });

  if (res.status === 0) {
    timeouts.add(1);
    console.log(`TIMEOUT vu=${__VU}`);
  } else if (!success) {
    errorRate.add(1);
    console.log(`FAIL vu=${__VU} status=${res.status} body=${(res.body || '').substring(0, 100)}`);
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

  console.log('Creating test data...');
  // Не удаляем старые — просто создаем новые
  
  const products = [];
  for (let i = 0; i < 50; i++) {
    const r = executeScript(`DB.table('products').create({name: 'stress-${i}', price: ${Math.floor(Math.random() * 1000) + 1}})`, token);
    if (r && r.result && r.result.id) {
      products.push(r.result.id);
    }
  }
  
  console.log(`Created ${products.length} products`);
  return { token, products };
}

export default function (data) {
  if (!data || !data.token) {
    return;
  }

  const rand = Math.random();
  const productId = data.products[__VU % data.products.length] || 1;

  if (rand < 0.7) {
    executeScript(`DB.table('products').findAll().slice(0, 50)`, data.token);
  } else if (rand < 0.85) {
    const price = Math.floor(Math.random() * 1000) + 1;
    executeScript(`DB.table('products').create({name: 'vu-${__VU}-${__ITER}', price: ${price}})`, data.token);
  } else if (rand < 0.95) {
    const price = Math.floor(Math.random() * 1000) + 1;
    executeScript(`DB.table('products').update(${productId}, {price: ${price}})`, data.token);
  } else {
    executeScript(`DB.table('products').count()`, data.token);
  }

  sleep(0.1);  // минимальная пауза, чтобы к6 не сходил с ума
}
