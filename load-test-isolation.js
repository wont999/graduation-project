import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const writeLatency = new Trend('write_latency');
const readLatency = new Trend('read_latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180/routing';
const KEYCLOAK_URL = 'http://localhost:8080/realms/appliner/protocol/openid-connect/token';

export const options = {
    setupTimeout: '120s',

    scenarios: {
        readers: {
            executor: 'per-vu-iterations',
            vus: 30,
            iterations: 1,
            exec: 'readScenario',
            startTime: '0s',
            maxDuration: '60s',
        },

        writers: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            exec: 'writeScenario',
            startTime: '2s',
            maxDuration: '60s',
        }
    }
};

function executeScript(script, token) {
    const payload = JSON.stringify({
        clientType: "blockly-executor",
        procedureName: "executeBlocklyScript",
        parameters: {
            script,
            parameters: {}
        }
    });

    const start = Date.now();

    const res = http.post(
        `${BASE_URL}/api/procedures/execute`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            timeout: '120s'
        }
    );

    const duration = Date.now() - start;

    let body = null;

    try {
        body = res.json();
    } catch (_) {}

    check(res, {
        'status is 200': r => r.status === 200,
    });

    return {
        success: body?.success === true,
        duration
    };
}

export function setup() {
    const res = http.post(
        KEYCLOAK_URL,
        {
            grant_type: 'password',
            client_id: 'gateway-client',
            client_secret: 'kZwCo8x0OioQvjmkXD9aY8FtYgJ6Z5Zs',
            username: 'testuser',
            password: 'testpass'
        },
        {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        }
    );

    return {
        token: res.json('access_token')
    };
}

/*
 * Цель:
 * занять execution slots на 30 секунд
 */
const BLOCKING_READ =
    `DB.query("SELECT pg_sleep(30)")`;

export function readScenario(data) {
    const result = executeScript(
        BLOCKING_READ,
        data.token
    );

    readLatency.add(result.duration);

    console.log(
        `READ finished in ${result.duration} ms`
    );
}

export function writeScenario(data) {

    const script =
        `DB.table('products').update(1,{price:${Math.floor(Math.random()*1000)}})`;

    const result = executeScript(
        script,
        data.token
    );

    writeLatency.add(result.duration);

    console.log(
        `WRITE finished in ${result.duration} ms`
    );
}