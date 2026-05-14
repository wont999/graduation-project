import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const readLatency = new Trend('read_latency');
const writeLatency = new Trend('write_latency');

export const options = {
    stages: [
        { duration: '3s', target: 10 },
        { duration: '7s', target: 50 },
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

  return {
    token: res.json('access_token'),
    refresh: res.json('refresh_token'),
  };
}

function executeScript(script, type, token) {
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
    console.log(`[${type}] FAIL status=${res.status} body=${(res.body || '').substring(0, 150)}`);
  } else {
    errorRate.add(0);
    if (type === 'read') readLatency.add(duration);
    else writeLatency.add(duration);
  }

  return res;
}

export default function (data) {
    if (!data || !data.token) {
        console.error('No token available, skipping iteration');
        return;
    }

    const rand = Math.random();

    if (rand < 0.7) {
        executeScript("DB.table('products').findAll()", 'read', data.token);
    } else if (rand < 0.85) {
        executeScript(`DB.table('products').create({name: 'k6-${__VU}-${__ITER}', price: 100})`, 'write', data.token);
    } else if (rand < 0.95) {
        executeScript("DB.table('products').count()", 'read', data.token);
    } else {
        executeScript("DB.table('products').where({name: 'k6-1-0'}).forEach(p => DB.table('products').update(p.id, {price: 200}))", 'write', data.token);
    }

    sleep(Math.random() * 1 + 0.5);
}