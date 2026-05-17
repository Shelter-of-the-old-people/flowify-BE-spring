package org.github.flowify.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.execution.dto.ExecutionCompleteRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionCompleteRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("FastAPI callback payload는 신규 content_metadata field와 snake/camel alias를 raw map으로 보존한다")
    void callbackPayloadPreservesRawContentMetadataFieldsAndAliases() throws Exception {
        ExecutionCompleteRequest request = objectMapper.readValue("""
                {
                  "status": "completed",
                  "durationMs": 1234,
                  "output": {
                    "type": "SINGLE_FILE",
                    "filename": "receipt.png",
                    "content_status": "available",
                    "content_metadata": {
                      "source_service": "gmail",
                      "message_id": "msg-1",
                      "messageId": "msg-1",
                      "attachment_id": "att-1",
                      "attachmentId": "att-1",
                      "mime_type": "image/png",
                      "mimeType": "image/png",
                      "inline": false,
                      "provider": "openai_vision",
                      "languages": ["ko", "en"],
                      "page_count": 3,
                      "ocr_page_count": 3,
                      "image_only_pdf": false,
                      "partial": true,
                      "image_width": 1200,
                      "image_height": 800,
                      "limits": {
                        "max_ocr_pages": 10,
                        "max_image_pixels": 12000000
                      }
                    }
                  }
                }
                """, ExecutionCompleteRequest.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> contentMetadata = (Map<String, Object>) request.getOutput().get("content_metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) contentMetadata.get("limits");

        assertThat(contentMetadata)
                .containsEntry("source_service", "gmail")
                .containsEntry("message_id", "msg-1")
                .containsEntry("messageId", "msg-1")
                .containsEntry("attachment_id", "att-1")
                .containsEntry("attachmentId", "att-1")
                .containsEntry("mime_type", "image/png")
                .containsEntry("mimeType", "image/png")
                .containsEntry("inline", false)
                .containsEntry("provider", "openai_vision")
                .containsEntry("page_count", 3)
                .containsEntry("ocr_page_count", 3)
                .containsEntry("image_only_pdf", false)
                .containsEntry("partial", true)
                .containsEntry("image_width", 1200)
                .containsEntry("image_height", 800);
        assertThat(contentMetadata.get("languages")).isEqualTo(java.util.List.of("ko", "en"));
        assertThat(limits)
                .containsEntry("max_ocr_pages", 10)
                .containsEntry("max_image_pixels", 12000000);
    }
}
