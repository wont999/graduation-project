import axios from 'axios'

const KEYCLOAK_URL = '/realms/appliner/protocol/openid-connect/token'

export async function getKeycloakToken() {
    const params = new URLSearchParams()
    params.append('grant_type', 'password')
    params.append('client_id', 'gateway-client')
    params.append('client_secret', 'rQJ3SCQxsVbdTH6IFqOLiG0fnVNbWpMP')
    params.append('username', 'testuser')
    params.append('password', 'testpass')

    const res = await axios.post(KEYCLOAK_URL, params, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    return res.data.access_token
}