import axios from 'axios'
import { getKeycloakToken } from './auth'

const API_BASE = '/api/procedures'

async function callProcedure(procedureName, parameters = {}) {
    const token = await getKeycloakToken()
    const res = await axios.post(
        `${API_BASE}/execute`,
        {
            procedureName,
            clientType: 'blockly-executor',
            parameters
        },
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
                'X-User-Id': 'testuser',
                'X-Organization-Id': 'appliner'
            }
        }
    )
    return res.data.result
}

export async function fetchSavedProcedures() {
    return callProcedure('getSavedProcedures', {})
}

export async function getSavedProcedure(name) {
    return callProcedure('getSavedProcedureByName', { name })
}

export async function saveProcedure({ name, description, blocklyXml, generatedJs }) {
    return callProcedure('saveProcedure', { name, description, blocklyXml, generatedJs })
}

export async function deleteSavedProcedure(name) {
    return callProcedure('deleteSavedProcedure', { name })
}