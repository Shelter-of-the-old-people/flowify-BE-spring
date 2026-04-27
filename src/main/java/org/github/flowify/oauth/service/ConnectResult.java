package org.github.flowify.oauth.service;

public sealed interface ConnectResult {

    record RedirectRequired(String authUrl) implements ConnectResult {}

    record DirectlyConnected(String service) implements ConnectResult {}
}
