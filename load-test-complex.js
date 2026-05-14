import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const opLatency = new Trend('op_latency');

export const options = {
    setupTimeout: '120s',
    stages: [
        { duration: '5s', target: 5 },
        { duration: '15s', target: 20 },
        { duration: '5s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<10000'],
        http_req_failed: ['rate<0.05'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8180/routing';
const KEYCLOAK_URL = 'http://host.docker.internal:8080/realms/appliner/protocol/openid-connect/token';

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
  try { bodyObj = res.json(); } catch (e) { /* not json */ }

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'success is true': () => bodyObj && bodyObj.success === true,
  });

  if (!success) {
    errorRate.add(1);
    console.log(`FAIL status=${res.status} body=${(res.body || '').substring(0, 200)}`);
  } else {
    errorRate.add(0);
    opLatency.add(duration);
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
  
  for (let i = 0; i < 20; i++) {
    executeScript(`DB.table('products').create({name: 'product-${i}', price: ${Math.floor(Math.random() * 1000) + 1}})`, token);
  }
  
  console.log('Test data created: 20 products');
  return { token };
}

export default function (data) {
  if (!data || !data.token) {
    console.error('No token');
    return;
  }

  const rand = Math.random();

  if (rand < 0.25) {
    // Сценарий 1: Полная выгрузка + агрегация
      executeScript(`
      var products = DB.table('products').findAll();
      var sum = 0;
      for (var i = 0; i < products.size(); i++) {
        sum += parseInt(products.get(i).price.toString());
      }
      var avg = sum / products.size();
      'count=' + products.size() + ', avg=' + avg;
    `, data.token);
    
  } else if (rand < 0.50) {
    // Сценарий 2: Batch update - увеличить цену всем на 10%
    executeScript(`
        var products = DB.table('products').findAll();
for (var i = 0; i < products.size(); i++) {
  var p = products.get(i);
  var price = parseInt(p.price.toString());
  DB.table('products').update(p.id, {price: Math.floor(price * 1.1)});
}
'updated all';
    `, data.token);
    
  } else if (rand < 0.75) {
    // Сценарий 3: Фильтрация в памяти + update (цена > 500 → скидка 20%)
    executeScript(`
var products = DB.table('products').findAll();
for (var i = 0; i < products.size(); i++) {
  var p = products.get(i);
  var price = parseInt(p.price.toString());
  if (price > 500) {
    DB.table('products').update(p.id, {price: Math.floor(price * 0.8)});
  }
}
'discounted expensive';
    `, data.token);
    
  } else {
    // Сценарий 4: Полная выгрузка + сортировка + top 10
      executeScript(`
      var products = DB.table('products').findAll();
      products.sort(function(a, b) { 
        return parseInt(b.price.toString()) - parseInt(a.price.toString()); 
      });
      var top = [];
      for (var i = 0; i < Math.min(3, products.size()); i++) {
        top.push(products.get(i).name);
      }
      top.join(', ');
    `, data.token);
  }

  sleep(1);
}
