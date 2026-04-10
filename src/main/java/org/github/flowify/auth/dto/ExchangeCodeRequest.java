package org.github.flowify.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExchangeCodeRequest {

    @NotBlank(message = "Exchange code는 필수입니다.")
    private String exchangeCode;
}
