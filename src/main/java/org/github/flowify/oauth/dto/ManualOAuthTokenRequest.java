package org.github.flowify.oauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ManualOAuthTokenRequest {

    @NotBlank(message = "Access token은 필수입니다.")
    private String accessToken;
}