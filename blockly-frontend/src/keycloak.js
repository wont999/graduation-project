import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
    url: 'http://localhost:8080',
    realm: 'appliner',
    clientId: 'blockly-frontend',
})

export default keycloak