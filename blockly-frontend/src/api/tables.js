import axios from 'axios'
import { getKeycloakToken } from './auth'

async function callProcedure(procedureName, params, token) {
    const res = await axios.post('/api/procedures/execute', {
        clientType: 'blockly-executor',
        procedureName,
        parameters: params,
    }, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'X-User-Id': 'testuser',
            'X-Organization-Id': 'default',
        },
        timeout: 30000,
    })
    return res.data.result
}

export async function fetchTables(tenant = 'appliner') {
    const token = await getKeycloakToken()
    return callProcedure('getTables', { tenant }, token)
}

export async function fetchTableColumns(tenant, table) {
    const token = await getKeycloakToken()
    return callProcedure('getTableColumns', { tenant, table }, token)
}