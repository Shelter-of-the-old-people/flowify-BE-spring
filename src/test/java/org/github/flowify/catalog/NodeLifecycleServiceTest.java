package org.github.flowify.catalog;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeLifecycleServiceTest {

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
    }

    @Nested
    @DisplayName("End Node (Sink) 검증")
    class EndNodeTests {

        @Test
        @DisplayName("Slack sink channel 빈 문자열 -> configured false")
        void slack_emptyChannel_notConfigured() {
            when(catalogService.getSinkRequiredFields("slack")).thenReturn(List.of("channel"));
            lenient().when(catalogService.isAuthRequired("slack")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink1")
                    .type("slack")
                    .role("end")
                    .config(Map.of("channel", ""))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.channel");
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
        @DisplayName("Gmail sink subject 빈 문자열 -> configured false")
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
            when(catalogService.getSinkRequiredFields("slack")).thenReturn(List.of("channel"));
            lenient().when(catalogService.isAuthRequired("slack")).thenReturn(true);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink7")
                    .type("slack")
                    .role("end")
                    .config(Map.of("channel", "C12345"))
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("필수 필드 값이 null이면 configured false")
        void nullRequiredField_notConfigured() {
            when(catalogService.getSinkRequiredFields("slack")).thenReturn(List.of("channel"));
            lenient().when(catalogService.isAuthRequired("slack")).thenReturn(true);

            Map<String, Object> config = new HashMap<>();
            config.put("channel", null);

            NodeDefinition node = NodeDefinition.builder()
                    .id("sink8")
                    .type("slack")
                    .role("end")
                    .config(config)
                    .build();

            NodeStatusResponse result = nodeLifecycleService.evaluate(node, null);

            assertThat(result.isConfigured()).isFalse();
            assertThat(result.getMissingFields()).contains("config.channel");
        }
    }
}
