package org.keycloak.test.framework.oauth;

import org.keycloak.test.framework.realm.ClientConfig;
import org.keycloak.test.framework.realm.ClientConfigBuilder;

public class DefaultOAuthClientConfiguration implements ClientConfig {

    @Override
    public ClientConfigBuilder configure(ClientConfigBuilder client) {
        return client.clientId("test-oauth-client")
                .serviceAccount()
                .redirectUris("http://127.0.0.1/callback/oauth")
                .secret("test-secret");
    }

}
