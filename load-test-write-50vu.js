import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('request_latency');
const timeouts = new Counter('timeouts');

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '10s', target: 50 },
        { duration: '60s', target: 50 },
        { duration: '10s', target: 0 },
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
  console.log('Auth successful');
  return { token };
}

export default function (data) {
  if (!data || !data.token) {
    return;
  }

  const price = Math.floor(Math.random() * 1000) + 1;
  const rand = Math.random();

  if (rand < 0.5) {
    executeScript(`DB.table('products').create({name: 'vu-${__VU}-${__ITER}', price: ${price}})`, data.token);
  } else {
    const id = Math.floor(Math.random() * 10000) + 1;
    executeScript(`DB.table('products').update(${id}, {price: ${price}})`, data.token);
  }

  sleep(0.1);
}
