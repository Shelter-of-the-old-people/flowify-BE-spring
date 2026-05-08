package org.github.flowify.catalog.service.picker;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.dto.picker.TargetOptionItem;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetOptionService {

    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private final CatalogService catalogService;
    private final OAuthTokenService oauthTokenService;
    private final GoogleDriveTargetOptionProvider googleDriveTargetOptionProvider;
    private final List<TargetOptionProvider> providers;

    public TargetOptionResponse getOptions(String userId, String serviceKey, String sourceMode,
                                           String parentId, String query, String cursor) {
        SourceService sourceService = catalogService.findSourceService(serviceKey);
        boolean supportedMode = sourceService.getSourceModes() != null
                && sourceService.getSourceModes().stream()
                .anyMatch(mode -> mode.getKey().equals(sourceMode));
        if (!supportedMode) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "source mode를 찾을 수 없습니다: " + sourceMode);
        }

        TargetOptionProvider provider = providers.stream()
                .filter(p -> p.getServiceKey().equals(serviceKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "target option provider를 찾을 수 없습니다: " + serviceKey));

        String token = null;
        if (sourceService.isAuthRequired() && !"canvas_lms".equals(serviceKey)) {
            token = oauthTokenService.getDecryptedToken(
                    userId, serviceKey, requiredScopes(serviceKey, sourceMode));
        }

        return provider.getOptions(sourceMode, token, parentId, query, cursor);
    }

    public TargetOptionItem createGoogleDriveFolder(String userId, String name, String parentId) {
        String token = oauthTokenService.getDecryptedToken(userId, "google_drive");
        return googleDriveTargetOptionProvider.createFolder(token, parentId, name);
    }

    private List<String> requiredScopes(String serviceKey, String sourceMode) {
        if ("gmail".equals(serviceKey)
                && ("label_emails".equals(sourceMode) || "single_email".equals(sourceMode))) {
            return List.of(GMAIL_READONLY_SCOPE);
        }
        return List.of();
    }
}
