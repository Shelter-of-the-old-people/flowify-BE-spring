package org.github.flowify.oauth.service;

public interface ManualTokenServiceHandler {

    String getServiceName();

    ManualTokenValidationResult validate(String accessToken);
}