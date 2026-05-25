import axios from 'axios'
import { getKeycloakToken } from './auth'

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