package org.github.flowify.catalog.service.picker;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SinkTargetOptionService {

    private final CatalogService catalogService;
    private final OAuthTokenService oauthTokenService;
    private final List<SinkTargetOptionProvider> providers;

    public TargetOptionResponse getOptions(
            String userId,
            String serviceKey,
            String type,
            String parentId,
            String query,
            String cursor
    ) {
        SinkService sinkService = catalogService.findSinkService(serviceKey);

        SinkTargetOptionProvider provider = providers.stream()
                .filter(candidate -> candidate.getServiceKey().equals(serviceKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "sink target option provider를 찾을 수 없습니다: " + serviceKey
                ));

        String token = null;
        if (sinkService.isAuthRequired()) {
            token = oauthTokenService.getDecryptedToken(userId, serviceKey);
        }

        return provider.getOptions(token, type, parentId, query, cursor);
    }
}
