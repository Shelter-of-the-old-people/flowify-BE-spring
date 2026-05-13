package org.github.flowify.oauth.service;

import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CanvasLmsConnector implements ExternalServiceConnector {

    @Override
    public String getServiceName() {
        return "canvas_lms";
    }

    @Override
    public ConnectResult connect(String userId) {
        log.warn("Manual token service connect was requested via OAuth endpoint: service=canvas_lms, userId={}", userId);
        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Canvas LMS는 계정 페이지에서 토큰을 직접 입력해 연결해야 합니다.");
    }
}