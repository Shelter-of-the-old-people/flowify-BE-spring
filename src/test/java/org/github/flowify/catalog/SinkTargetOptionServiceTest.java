package org.github.flowify.catalog;

import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.SinkTargetOptionProvider;
import org.github.flowify.catalog.service.picker.SinkTargetOptionService;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SinkTargetOptionServiceTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private OAuthTokenService oauthTokenService;

    @Mock
    private SinkTargetOptionProvider sinkTargetOptionProvider;

    private SinkTargetOptionService sinkTargetOptionService;

    @BeforeEach
    void setUp() {
        sinkTargetOptionService = new SinkTargetOptionService(
                catalogService,
                oauthTokenService,
                List.of(sinkTargetOptionProvider)
        );
    }

    @Test
    void getOptions_usesStoredOauthTokenForSinkProvider() {
        SinkService slackService = new SinkService(
                "slack",
                "Slack",
                true,
                List.of("TEXT"),
                "per_service",
                Map.of()
        );
        TargetOptionResponse response = TargetOptionResponse.builder()
                .items(List.of())
                .nextCursor(null)
                .build();

        when(catalogService.findSinkService("slack")).thenReturn(slackService);
        when(oauthTokenService.getDecryptedToken("user-1", "slack")).thenReturn("slack-token");
        when(sinkTargetOptionProvider.getServiceKey()).thenReturn("slack");
        when(sinkTargetOptionProvider.getOptions("slack-token", "channel", null, "flow", null))
                .thenReturn(response);

        TargetOptionResponse result = sinkTargetOptionService.getOptions(
                "user-1", "slack", "channel", null, "flow", null);

        assertThat(result).isSameAs(response);
        verify(oauthTokenService).getDecryptedToken("user-1", "slack");
    }
}
