import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const errors = new Counter('errors');
const rejected = new Counter('rejected');
const accepted = new Counter('accepted');
const latency = new Trend('request_latency');

export const options = {
    scenarios: {
        ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 300 },
                { duration: '30s', target: 300 },
                { duration: '5s', target: 0 },
            ],
        },
    },
};

const ROUTING_URL = __ENV.ROUTING_URL || 'http://localhost:8081';
const SCRIPT = `DB.table('products').findAll()`;

export function setup() {
    return { token: 'dummy' };
}

export default function (data) {
    const payload = JSON.stringify({
        clientType: 'blockly-executor',
        procedureName: 'executeBlocklyScript',
        parameters: { script: SCRIPT, parameters: {} },
    });

    const res = http.post(`${ROUTING_URL}/api/procedures/execute`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': 'testuser',
            'X-Organization-Id': 'appliner',
        },
        timeout: '30s',
    });

    let bodyObj = null;
    try { bodyObj = res.json(); } catch (e) {}

    if (res.status === 0) {
        errors.add(1);
    } else if (res.status === 429 || (bodyObj && bodyObj.errorMessage && bodyObj.errorMessage.includes('capacity'))) {
        rejected.add(1);
    } else if (res.status === 200 && bodyObj && bodyObj.success) {
        accepted.add(1);
        latency.add(res.timings.duration);
    } else {
        errors.add(1);
    }
}
