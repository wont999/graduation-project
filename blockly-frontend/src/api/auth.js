export async function getKeycloakToken() {
    const kc = window.__keycloak
    if (!kc || !kc.token) throw new Error('Not authenticated')
    try {
        await kc.updateToken(30)
    } catch {
        kc.login()
        throw new Error('Token refresh failed')
    }
    return kc.token
}

export function getTenantFromToken(token) {
    if (!token) token = window.__keycloak?.token
    if (!token) throw new Error('No token available to extract tenant')
    try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        if (payload.groups && payload.groups.length > 0) return payload.groups[0]
    } catch {}
    throw new Error('Tenant not found in token groups')
}

export function logout() {
    const kc = window.__keycloak
    if (kc) kc.logout({ redirectUri: window.location.origin })
}
