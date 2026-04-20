package org.github.flowify.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    AUTH_OAUTH_FAILED(HttpStatus.BAD_REQUEST, "OAuth 인증에 실패했습니다."),
    AUTH_EXCHANGE_CODE_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 교환 코드입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // Workflow
    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "워크플로우를 찾을 수 없습니다."),
    WORKFLOW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "워크플로우 접근 권한이 없습니다."),
    WORKFLOW_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "워크플로우 유효성 검증에 실패했습니다."),

    // OAuth
    OAUTH_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "필요한 서비스가 연결되지 않았습니다."),
    OAUTH_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "외부 서비스 토큰 갱신에 실패했습니다."),

    // Template
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다."),

    // Execution
    EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "워크플로우 실행에 실패했습니다."),
    EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "실행 이력을 찾을 수 없습니다."),

    // External API
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 API 호출에 실패했습니다."),
    LLM_API_ERROR(HttpStatus.BAD_GATEWAY, "LLM API 호출에 실패했습니다."),
    LLM_GENERATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "LLM 기반 워크플로우 생성에 실패했습니다."),
    CRAWL_FAILED(HttpStatus.BAD_GATEWAY, "웹 수집에 실패했습니다."),
    DATA_CONVERSION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "데이터 규격 변환에 실패했습니다."),

    // Catalog
    CATALOG_SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "카탈로그에서 서비스를 찾을 수 없습니다."),
    CATALOG_INVALID_INPUT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 입력 타입입니다."),
    PREFLIGHT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "실행 전 검증에 실패했습니다."),

    // FastAPI
    FASTAPI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 접근할 수 없습니다."),

    // Common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
