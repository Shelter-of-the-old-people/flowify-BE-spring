package org.github.flowify.catalog;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeLifecycleServiceTest {

    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    @Mock
    private CatalogService catalogService;
    @Mock
    private OAuthTokenService oauthTokenService;

    private NodeLifecycleService nodeLifecycleService;

    @BeforeEach
    void setUp() {
        nodeLifecycleService = new NodeLifecycleService(catalogService, oauthTokenService);
    }

    @Nested
    @DisplayName("Start Node 검증")
    class StartNodeTests {

        @Test
        @DisplayName("Google Drive folder_new_file, target 빈 문자열 -> configured false")
        void googleDrive_folderNewFile_emptyTarget_notConfigured() {
            when(catalogService.isSourceTargetRequired("google_drive", "folder_new_file")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node1")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("SINGLE_FILE")
                    .config(Map.of(
                            "source_mode", "folder_new_file",
                            "target", "",
                            "target_label", ""
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target");
        }

        @Test
        @DisplayName("Google Drive single_file, target 빈 문자열 -> configured false")
        void googleDrive_singleFile_emptyTarget_notConfigured() {
            when(catalogService.isSourceTargetRequired("google_drive", "single_file")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node2")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("SINGLE_FILE")
                    .config(Map.of(
                            "source_mode", "single_file",
                            "target", ""
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target");
        }

        @Test
        @DisplayName("Gmail new_email, target 없음 -> target_schema 비어 있으므로 target 때문에 실패하지 않음")
        void gmail_newEmail_noTarget_configuredIfOthersPresent() {
            when(catalogService.isSourceTargetRequired("gmail", "new_email")).thenReturn(false);
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node3")
                    .type("gmail")
                    .role("start")
                    .outputDataType("SINGLE_EMAIL")
                    .config(Map.of("source_mode", "new_email"))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.getMissingFields()).isNull();
        }

        @Test
        @DisplayName("Gmail sender_email, target 빈 문자열 -> configured false")
        void gmail_senderEmail_emptyTarget_notConfigured() {
            when(catalogService.isSourceTargetRequired("gmail", "sender_email")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("gmail-sender-empty")
                    .type("gmail")
                    .role("start")
                    .outputDataType("SINGLE_EMAIL")
                    .config(Map.of(
                            "source_mode", "sender_email",
                            "target", ""
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target");
        }

        @Test
        @DisplayName("Gmail sender_email, target 있으면 configured true")
        void gmail_senderEmail_targetPresent_configured() {
            when(catalogService.isSourceTargetRequired("gmail", "sender_email")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("gmail-sender")
                    .type("gmail")
                    .role("start")
                    .outputDataType("SINGLE_EMAIL")
                    .config(Map.of(
                            "source_mode", "sender_email",
                            "target", "sender@example.com"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.getMissingFields()).isNull();
        }

        @Test
        @DisplayName("source_mode 빈 문자열 -> configured false")
        void emptySourceMode_notConfigured() {
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node4")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("SINGLE_FILE")
                    .config(Map.of(
                            "source_mode", "",
                            "target", "some_folder_id"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.source_mode");
        }

        @Test
        @DisplayName("source_mode가 문자열이 아니면 configured false")
        void nonStringSourceMode_notConfigured() {
            NodeDefinition node = NodeDefinition.builder()
                    .id("node4-non-string")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("SINGLE_FILE")
                    .config(Map.of(
                            "source_mode", List.of("folder_new_file"),
                            "target", "folder_id"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.source_mode");
        }

        @Test
        @DisplayName("프론트가 isConfigured=false를 보낸 경우 configured를 true로 뒤집지 않음")
        void frontendIsConfiguredFalse_respectsIt() {
            when(catalogService.isSourceTargetRequired("google_drive", "folder_new_file")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            Map<String, Object> config = new HashMap<>();
            config.put("source_mode", "folder_new_file");
            config.put("target", "valid_folder_id");
            config.put("isConfigured", false);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node5")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("SINGLE_FILE")
                    .config(config)
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("outputDataType 빈 문자열 -> configured false")
        void emptyOutputDataType_notConfigured() {
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node6")
                    .type("google_drive")
                    .role("start")
                    .outputDataType("")
                    .config(Map.of(
                            "source_mode", "folder_new_file",
                            "target", "folder_id"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("outputDataType");
        }

        @Test
        @DisplayName("GitHub new_pr, owner/repo 형식이 아니면 configured false")
        void githubNewPr_invalidTarget_notConfigured() {
            when(catalogService.isSourceTargetRequired("github", "new_pr")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("github")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("github-start-invalid")
                    .type("github")
                    .role("start")
                    .outputDataType("API_RESPONSE")
                    .config(Map.of(
                            "source_mode", "new_pr",
                            "target", "https://github.com/openai/openai-python"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target");
        }

        @Test
        @DisplayName("GitHub new_pr, owner/repo 형식이면 configured true")
        void githubNewPr_ownerRepoTarget_configured() {
            when(catalogService.isSourceTargetRequired("github", "new_pr")).thenReturn(true);
            lenient().when(catalogService.isAuthRequired("github")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("github-start-valid")
                    .type("github")
                    .role("start")
                    .outputDataType("API_RESPONSE")
                    .config(Map.of(
                            "source_mode", "new_pr",
                            "target", "openai/openai-python"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
        }
    }

    @Nested
    @DisplayName("End Node (Sink) 검증")
    class EndNodeTests {

        @Test
        @DisplayName("Notion sink target_type 빈 문자열 -> configured false")
        void notion_emptyTargetType_notConfigured() {
            when(catalogService.getSinkRequiredFields("notion")).thenReturn(List.of("target_type", "target_id"));
            lenient().when(catalogService.isAuthRequired("notion")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink1")
                    .type("notion")
                    .role("end")
                    .config(Map.of(
                            "target_type", "",
                            "target_id", "page_123"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target_type");
        }

        @Test
        @DisplayName("Gmail sink to 빈 문자열 -> configured false")
        void gmail_emptyTo_notConfigured() {
            when(catalogService.getSinkRequiredFields("gmail")).thenReturn(List.of("to", "subject", "action"));
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink2")
                    .type("gmail")
                    .role("end")
                    .config(Map.of(
                            "to", "",
                            "subject", "Test Subject",
                            "action", "send"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.to");
        }

        @Test
        @DisplayName("Gmail sink current user email source satisfies recipient")
        void gmail_currentUserEmailSource_configured() {
            when(catalogService.getSinkRequiredFields("gmail")).thenReturn(List.of("to", "subject", "action"));
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink-current-user")
                    .type("gmail")
                    .role("end")
                    .config(Map.of(
                            "to_source", "current_user_email",
                            "subject", "Test Subject",
                            "action", "send"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.getMissingFields()).isNull();
        }

        @Test
        @DisplayName("Gmail sink empty subject is not configured")
        void gmail_emptySubject_notConfigured() {
            when(catalogService.getSinkRequiredFields("gmail")).thenReturn(List.of("to", "subject", "action"));
            lenient().when(catalogService.isAuthRequired("gmail")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink3")
                    .type("gmail")
                    .role("end")
                    .config(Map.of(
                            "to", "test@example.com",
                            "subject", "",
                            "action", "send"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.subject");
        }

        @Test
        @DisplayName("Notion sink target_id 빈 문자열 -> configured false")
        void notion_emptyTargetId_notConfigured() {
            when(catalogService.getSinkRequiredFields("notion")).thenReturn(List.of("target_type", "target_id"));
            lenient().when(catalogService.isAuthRequired("notion")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink4")
                    .type("notion")
                    .role("end")
                    .config(Map.of(
                            "target_type", "page",
                            "target_id", ""
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target_id");
        }

        @Test
        @DisplayName("Google Drive sink folder_id 빈 문자열 -> configured false")
        void googleDrive_emptyFolderId_notConfigured() {
            when(catalogService.getSinkRequiredFields("google_drive")).thenReturn(List.of("folder_id"));
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink5")
                    .type("google_drive")
                    .role("end")
                    .config(Map.of("folder_id", ""))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.folder_id");
        }

        @Test
        @DisplayName("Google Drive sink 파일명 설정은 optional이므로 누락되어도 configured true")
        void googleDrive_missingOptionalFilenameConfig_configured() {
            when(catalogService.getSinkRequiredFields("google_drive")).thenReturn(List.of("folder_id"));
            lenient().when(catalogService.isAuthRequired("google_drive")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink5-optional-filename")
                    .type("google_drive")
                    .role("end")
                    .config(Map.of("folder_id", "folder_123"))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.getMissingFields()).isNull();
        }

        @Test
        @DisplayName("Google Sheets sink spreadsheet_id 빈 문자열 -> configured false")
        void googleSheets_emptySpreadsheetId_notConfigured() {
            when(catalogService.getSinkRequiredFields("google_sheets"))
                    .thenReturn(List.of("spreadsheet_id", "write_mode"));
            lenient().when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink6")
                    .type("google_sheets")
                    .role("end")
                    .config(Map.of(
                            "spreadsheet_id", "",
                            "write_mode", "append",
                            "sheet_name", "Sheet1"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.spreadsheet_id");
        }

        @Test
        @DisplayName("모든 필수 필드가 유효한 값이면 configured true")
        void allRequiredFieldsPresent_configured() {
            when(catalogService.getSinkRequiredFields("notion")).thenReturn(List.of("target_type", "target_id"));
            lenient().when(catalogService.isAuthRequired("notion")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink7")
                    .type("notion")
                    .role("end")
                    .config(Map.of(
                            "target_type", "page",
                            "target_id", "page_123"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("필수 필드 값이 null이면 configured false")
        void nullRequiredField_notConfigured() {
            when(catalogService.getSinkRequiredFields("notion")).thenReturn(List.of("target_type", "target_id"));
            lenient().when(catalogService.isAuthRequired("notion")).thenReturn(true);

            Map<String, Object> config = new HashMap<>();
            config.put("target_type", "page");
            config.put("target_id", null);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink8")
                    .type("notion")
                    .role("end")
                    .config(config)
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.target_id");
        }

        @Test
        @DisplayName("Discord sink webhook_url 빈 문자열이면 configured false")
        void discord_emptyWebhookUrl_notConfigured() {
            when(catalogService.getSinkRequiredFields("discord")).thenReturn(List.of("webhook_url"));

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink-discord-empty")
                    .type("discord")
                    .role("end")
                    .config(Map.of("webhook_url", ""))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.webhook_url");
            verifyNoInteractions(oauthTokenService);
        }

        @Test
        @DisplayName("Discord sink webhook_url 있으면 OAuth 없이 executable true")
        void discord_webhookUrlPresent_executableWithoutOauth() {
            when(catalogService.getSinkRequiredFields("discord")).thenReturn(List.of("webhook_url"));

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink-discord")
                    .type("discord")
                    .role("end")
                    .config(Map.of("webhook_url", "https://discord.com/api/webhooks/test/token"))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.isExecutable()).isTrue();
            assertThat(result.getMissingFields()).isNull();
            verifyNoInteractions(oauthTokenService);
        }
    }

    @Nested
    @DisplayName("OAuth 토큰 검증")
    class OAuthTokenTests {

        @Test
        @DisplayName("토큰 미연결 시 missingFields에 oauth_token 추가")
        void oauthNotConnected_addsOauthToken() {
            when(catalogService.isSourceTargetRequired("google_sheets", "sheet_all")).thenReturn(true);
            when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);
            when(oauthTokenService.getDecryptedToken(eq("user1"), eq("google_sheets"), anyList()))
                    .thenThrow(new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED));

            NodeDefinition node = NodeDefinition.builder()
                    .id("node-oauth1")
                    .type("google_sheets")
                    .role("start")
                    .outputDataType("SPREADSHEET_DATA")
                    .config(Map.of(
                            "source_mode", "sheet_all",
                            "target", "spreadsheet_123",
                            "sheet_name", "Sheet1"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.isExecutable()).isFalse();
            assertThat(result.getMissingFields()).contains("oauth_token");
            assertThat(result.getMissingFields()).doesNotContain("oauth_scope_insufficient");
        }

        @Test
        @DisplayName("scope 부족 시 missingFields에 oauth_scope_insufficient 추가")
        void oauthScopeInsufficient_addsOauthScopeInsufficient() {
            when(catalogService.isSourceTargetRequired("google_sheets", "sheet_all")).thenReturn(true);
            when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);
            when(oauthTokenService.getDecryptedToken(eq("user1"), eq("google_sheets"), anyList()))
                    .thenThrow(new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT));

            NodeDefinition node = NodeDefinition.builder()
                    .id("node-oauth2")
                    .type("google_sheets")
                    .role("start")
                    .outputDataType("SPREADSHEET_DATA")
                    .config(Map.of(
                            "source_mode", "sheet_all",
                            "target", "spreadsheet_123",
                            "sheet_name", "Sheet1"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.isExecutable()).isFalse();
            assertThat(result.getMissingFields()).contains("oauth_scope_insufficient");
            assertThat(result.getMissingFields()).doesNotContain("oauth_token");
        }

        @Test
        @DisplayName("status check evaluation uses OAuth status validation")
        void evaluateAllForStatusCheck_usesStatusValidation() {
            when(catalogService.isSourceTargetRequired("google_sheets", "sheet_all")).thenReturn(true);
            when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("node-status")
                    .type("google_sheets")
                    .role("start")
                    .outputDataType("SPREADSHEET_DATA")
                    .config(Map.of(
                            "source_mode", "sheet_all",
                            "target", "spreadsheet_123",
                            "sheet_name", "Sheet1"
                    ))
                    .build();

            List<NodeStatusResponse> results =
                    nodeLifecycleService.evaluateAllForStatusCheck(List.of(node), "user1");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isExecutable()).isTrue();
            verify(oauthTokenService).validateTokenForStatusCheck(eq("user1"), eq("google_sheets"), anyList());
            verify(oauthTokenService, never()).getDecryptedToken(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("status check evaluation keeps OAuth error mapping")
        void evaluateAllForStatusCheck_mapsOauthErrors() {
            when(catalogService.isSourceTargetRequired("google_sheets", "sheet_all")).thenReturn(true);
            when(catalogService.isAuthRequired("google_sheets")).thenReturn(true);
            doThrow(new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT))
                    .when(oauthTokenService)
                    .validateTokenForStatusCheck(eq("user1"), eq("google_sheets"), anyList());

            NodeDefinition node = NodeDefinition.builder()
                    .id("node-status-error")
                    .type("google_sheets")
                    .role("start")
                    .outputDataType("SPREADSHEET_DATA")
                    .config(Map.of(
                            "source_mode", "sheet_all",
                            "target", "spreadsheet_123",
                            "sheet_name", "Sheet1"
                    ))
                    .build();

            NodeStatusResponse result =
                    nodeLifecycleService.evaluateAllForStatusCheck(List.of(node), "user1").get(0);

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.isExecutable()).isFalse();
            assertThat(result.getMissingFields()).contains("oauth_scope_insufficient");
            assertThat(result.getMissingFields()).doesNotContain("oauth_token");
            verify(oauthTokenService, never()).getDecryptedToken(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("Gmail source는 readonly scope로 토큰을 검증한다")
        void gmailSource_requiresReadonlyScope() {
            when(catalogService.isSourceTargetRequired("gmail", "new_email")).thenReturn(false);
            when(catalogService.isAuthRequired("gmail")).thenReturn(true);
            when(oauthTokenService.getDecryptedToken(eq("user1"), eq("gmail"), anyList()))
                    .thenReturn("gmail-token");

            NodeDefinition node = NodeDefinition.builder()
                    .id("gmail-source")
                    .type("gmail")
                    .role("start")
                    .outputDataType("SINGLE_EMAIL")
                    .config(Map.of("source_mode", "new_email"))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isExecutable()).isTrue();
            verify(oauthTokenService).getDecryptedToken("user1", "gmail", List.of(GMAIL_READONLY_SCOPE));
        }

        @Test
        @DisplayName("Gmail sender_email source는 target 설정 후 readonly scope로 토큰을 검증한다")
        void gmailSenderEmailSource_requiresReadonlyScope() {
            when(catalogService.isSourceTargetRequired("gmail", "sender_email")).thenReturn(true);
            when(catalogService.isAuthRequired("gmail")).thenReturn(true);
            when(oauthTokenService.getDecryptedToken(eq("user1"), eq("gmail"), anyList()))
                    .thenReturn("gmail-token");

            NodeDefinition node = NodeDefinition.builder()
                    .id("gmail-sender-source")
                    .type("gmail")
                    .role("start")
                    .outputDataType("SINGLE_EMAIL")
                    .config(Map.of(
                            "source_mode", "sender_email",
                            "target", "sender@example.com"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isConfigured()).isTrue();
            assertThat(result.isExecutable()).isTrue();
            verify(oauthTokenService).getDecryptedToken("user1", "gmail", List.of(GMAIL_READONLY_SCOPE));
        }

        @Test
        @DisplayName("Gmail sink는 send scope로 토큰을 검증한다")
        void gmailSink_requiresSendScope() {
            when(catalogService.getSinkRequiredFields("gmail")).thenReturn(List.of("to", "subject", "action"));
            when(catalogService.isAuthRequired("gmail")).thenReturn(true);
            when(oauthTokenService.getDecryptedToken(eq("user1"), eq("gmail"), anyList()))
                    .thenReturn("gmail-token");

            NodeDefinition node = NodeDefinition.builder()
                    .id("gmail-sink")
                    .type("gmail")
                    .role("end")
                    .config(Map.of(
                            "to", "receiver@example.com",
                            "subject", "Hello",
                            "action", "send"
                    ))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, "user1");

            assertThat(result.isExecutable()).isTrue();
            verify(oauthTokenService).getDecryptedToken("user1", "gmail", List.of(GMAIL_SEND_SCOPE));
        }
    }
}
