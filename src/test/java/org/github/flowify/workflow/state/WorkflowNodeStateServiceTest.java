package org.github.flowify.workflow.state;

import org.github.flowify.execution.dto.NodeStateUpdateRequest;
import org.github.flowify.workflow.state.entity.WorkflowNodeState;
import org.github.flowify.workflow.state.repository.WorkflowNodeStateRepository;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowNodeStateServiceTest {

    @Mock
    private WorkflowNodeStateRepository workflowNodeStateRepository;

    @InjectMocks
    private WorkflowNodeStateService workflowNodeStateService;

    @Test
    @DisplayName("encodes dotted row snapshot keys before save")
    void applyUpdates_encodesNestedMapKeysBeforeSave() {
        when(workflowNodeStateRepository.findByWorkflowIdAndNodeId("wf-1", "node-start"))
                .thenReturn(Optional.empty());

        workflowNodeStateService.applyUpdates(
                "wf-1",
                List.of(new NodeStateUpdateRequest(
                        "node-start",
                        "google_sheets",
                        Map.of(
                                "last_seen_row_index", 2,
                                "row_snapshot", Map.of(
                                        "alice@example.com", "hash-a",
                                        "bob.smith@example.com", "hash-b"
                                )
                        )
                ))
        );

        ArgumentCaptor<WorkflowNodeState> captor = ArgumentCaptor.forClass(WorkflowNodeState.class);
        verify(workflowNodeStateRepository).save(captor.capture());

        WorkflowNodeState saved = captor.getValue();
        assertThat(saved.getWorkflowId()).isEqualTo("wf-1");
        assertThat(saved.getNodeId()).isEqualTo("node-start");
        assertThat(saved.getService()).isEqualTo("google_sheets");
        assertThat(saved.getState()).containsEntry("last_seen_row_index", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> storedSnapshot = (Map<String, Object>) saved.getState().get("row_snapshot");
        assertThat(storedSnapshot).containsEntry("alice@example%2Ecom", "hash-a");
        assertThat(storedSnapshot).containsEntry("bob%2Esmith@example%2Ecom", "hash-b");
    }

    @Test
    @DisplayName("decodes stored row snapshot keys after read")
    void getStateMap_decodesNestedMapKeysAfterRead() {
        LinkedHashMap<String, Object> storedSnapshot = new LinkedHashMap<>();
        storedSnapshot.put("alice@example%2Ecom", "hash-a");
        storedSnapshot.put("bob%2Esmith@example%2Ecom", "hash-b");

        when(workflowNodeStateRepository.findByWorkflowId("wf-1"))
                .thenReturn(List.of(WorkflowNodeState.builder()
                        .workflowId("wf-1")
                        .nodeId("node-start")
                        .service("google_sheets")
                        .state(new LinkedHashMap<>(Map.of(
                                "last_seen_row_index", 2,
                                "row_snapshot", storedSnapshot
                        )))
                        .build()));

        Map<String, Map<String, Object>> stateMap = workflowNodeStateService.getStateMap("wf-1");

        assertThat(stateMap).containsKey("node-start");
        assertThat(stateMap.get("node-start")).containsEntry("last_seen_row_index", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> decodedSnapshot = (Map<String, Object>) stateMap.get("node-start").get("row_snapshot");
        assertThat(decodedSnapshot).containsEntry("alice@example.com", "hash-a");
        assertThat(decodedSnapshot).containsEntry("bob.smith@example.com", "hash-b");
    }
}
