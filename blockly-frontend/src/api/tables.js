import axios from 'axios'
import { getKeycloakToken, getTenantFromToken } from './auth'

async function callProcedure(procedureName, params, token) {
    const tenant = getTenantFromToken(token)
    const res = await axios.post('/api/procedures/execute', {
        clientType: 'blockly-executor',
        procedureName,
        parameters: params,
    }, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'X-Organization-Id': tenant,
        },
        timeout: 30000,
    })
    return res.data.result
}

export async function fetchTables() {
    const token = await getKeycloakToken()
    const tenant = getTenantFromToken(token)
    return callProcedure('getTables', { tenant }, token)
}

export async function fetchTableColumns(table) {
    const token = await getKeycloakToken()
    const tenant = getTenantFromToken(token)
    return callProcedure('getTableColumns', { tenant, table }, token)
}
