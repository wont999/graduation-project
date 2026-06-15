import axios from 'axios'
import { getKeycloakToken } from './auth'

function authHeaders(token) {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'X-User-Id': 'testuser',
        'X-Organization-Id': 'appliner',
    }
}

export async function executeScript(script) {
    const token = await getKeycloakToken()
    const res = await axios.post(
        '/api/procedures/execute',
        {
            clientType: 'blockly-executor',
            procedureName: 'executeBlocklyScript',
            parameters: {
                script: script,
                parameters: {},
            },
        },
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
                'X-User-Id': 'testuser',
                'X-Organization-Id': 'default',
            },
            timeout: 60000,
        }
    )
    return res.data
}

export async function executeScriptAsync(script) {
    const token = await getKeycloakToken()
    const submit = await axios.post('/api/procedures/submit', {
        clientType: 'blockly-executor',
        procedureName: 'executeBlocklyScript',
        parameters: { script, parameters: {} },
    }, { headers: authHeaders(token) })

    const { requestId } = submit.data
    return pollResult(requestId, token)
}

async function pollResult(requestId, token, intervalMs = 1500, timeoutMs = 120000) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
        const res = await axios.get(`/api/executions/${requestId}`, { headers: authHeaders(token) })
        const { status, response } = res.data
        if (status === 'DONE') return JSON.parse(response)
        if (status === 'FAILED') throw new Error(response || 'Execution failed')
        await new Promise(r => setTimeout(r, intervalMs))
    }
    throw new Error('Polling timeout')
}