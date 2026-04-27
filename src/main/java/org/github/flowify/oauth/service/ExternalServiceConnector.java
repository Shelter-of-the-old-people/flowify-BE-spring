package org.github.flowify.oauth.service;

public interface ExternalServiceConnector {

    String getServiceName();

    ConnectResult connect(String userId);

    default boolean supportsCallback() {
        return false;
    }

    default void handleCallback(String code, String state) {
        throw new UnsupportedOperationException(
                getServiceName() + " does not support OAuth callback");
    }
}