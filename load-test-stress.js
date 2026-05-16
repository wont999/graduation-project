import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');

export const options = {
    setupTimeout: '300s',
    stages: [
        { duration: '30s', target: 10 },     // разогрев
        { duration: '1m', target: 50 },      // 50 VU
        { duration: '1m', target: 100 },     // 100 VU
        { duration: '1m', target: 150 },     // 150 VU
        { duration: '1m', target: 200 },     // 200 VU - ищем предел
        { duration: '30s', target: 0 },      // сброс
    ],
    thresholds: {
        http_req_failed: ['rate<0.1'],        // до 10% ошибок допустимо
    },
};



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
  try { bodyObj = res.json(); } catch (e) {}

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'success is true': () => bodyObj && bodyObj.success === true,
  });

  if (!success) {
    errorRate.add(1);
    console.log(`FAIL vu=${__VU} status=${res.status} body=${(res.body || '').substring(0, 150)}`);
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

  console.log('Creating test data...');
  executeScript("DB.table('products').findAll().forEach(p => DB.table('products').delete(p.id))", token);
  
  // Создаем 200 продуктов
  const products = [];
  for (let i = 0; i < 200; i++) {
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
    // 70% - read
    executeScript(`DB.table('products').findAll()`, data.token);
    
  } else if (rand < 0.85) {
    // 15% - create
    const price = Math.floor(Math.random() * 1000) + 1;
    executeScript(`DB.table('products').create({name: 'vu-${__VU}-${__ITER}', price: ${price}})`, data.token);
    
  } else if (rand < 0.95) {
    // 10% - update
    const price = Math.floor(Math.random() * 1000) + 1;
    executeScript(`DB.table('products').update(${productId}, {price: ${price}})`, data.token);
    
  } else {
    // 5% - count
    executeScript(`DB.table('products').count()`, data.token);
  }

  sleep(Math.random() * 0.5);
}
