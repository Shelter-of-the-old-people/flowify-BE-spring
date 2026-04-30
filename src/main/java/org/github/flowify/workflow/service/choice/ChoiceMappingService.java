package org.github.flowify.workflow.service.choice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.BranchConfig;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.FollowUp;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChoiceMappingService {

    private final ObjectMapper objectMapper;

    @Value("${app.mapping-rules.path:docs/mapping_rules.json}")
    private String mappingRulesPath;

    private MappingRules mappingRules;

    @PostConstruct
    private void loadMappingRules() {
        try {
            ClassPathResource resource = new ClassPathResource(mappingRulesPath);
            try (InputStream is = resource.getInputStream()) {
                mappingRules = objectMapper.readValue(is, MappingRules.class);
            }
            log.info("Loaded mapping rules v{} with {} data types",
                    mappingRules.getMeta().getVersion(),
                    mappingRules.getDataTypes().size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mapping_rules.json from " + mappingRulesPath, e);
        }
    }

    /**
     * 핵심 메서드: 이전 노드의 outputDataType 기반으로 선택지를 반환한다.
     * requires_processing_method가 true이면 처리 방식 선택지를,
     * false이면 바로 actions 선택지를 반환한다.
     */
    public ChoiceResponse getOptionsForNode(String previousOutputType, Map<String, Object> context) {
        DataTypeConfig config = getDataTypeConfig(previousOutputType);

        if (config.isRequiresProcessingMethod()) {
            return getProcessingMethodChoices(previousOutputType);
        }

        List<Action> filteredActions = filterByApplicableWhen(config.getActions(), context);
        filteredActions.sort(Comparator.comparingInt(Action::getPriority));

        List<Option> options = filteredActions.stream()
                .map(action -> Option.builder()
                        .id(action.getId())
                        .label(action.getLabel())
                        .build())
                .toList();

        return ChoiceResponse.builder()
                .question(config.getLabel() + "을(를) 어떻게 처리할까요?")
                .options(options)
                .requiresProcessingMethod(false)
                .build();
    }

    /**
     * 1차 처리 방식 선택지 반환 (requires_processing_method가 true인 데이터 타입용).
     */
    public ChoiceResponse getProcessingMethodChoices(String dataType) {
        DataTypeConfig config = getDataTypeConfig(dataType);

        if (!config.isRequiresProcessingMethod() || config.getProcessingMethod() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "데이터 타입 '" + dataType + "'은 처리 방식 선택이 필요하지 않습니다.");
        }

        List<Option> options = config.getProcessingMethod().getOptions().stream()
                .map(opt -> Option.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .build())
                .toList();

        return ChoiceResponse.builder()
                .question(config.getProcessingMethod().getQuestion())
                .options(options)
                .requiresProcessingMethod(true)
                .build();
    }

    /**
     * 사용자 선택 처리. 노드 타입을 결정하고 follow_up/branch_config이 있으면 후속 설정을 반환한다.
     * follow_up/branch_config에 options_source가 있으면 context 기반으로 동적 옵션을 resolve하여 반환한다.
     *
     * processing method 선택인 경우: processing_method.options에서 찾음
     * action 선택인 경우: actions에서 찾음
     */
    public NodeSelectionResult onUserSelect(String selectedOptionId, String dataType,
                                            Map<String, Object> context) {
        DataTypeConfig config = getDataTypeConfig(dataType);

        // 1. processing_method 옵션에서 찾기
        if (config.isRequiresProcessingMethod() && config.getProcessingMethod() != null) {
            for (Option opt : config.getProcessingMethod().getOptions()) {
                if (opt.getId().equals(selectedOptionId)) {
                    return NodeSelectionResult.builder()
                            .nodeType(opt.getNodeType())
                            .outputDataType(opt.getOutputDataType())
                            .build();
                }
            }
        }

        // 2. actions에서 찾기
        if (config.getActions() != null) {
            for (Action action : config.getActions()) {
                if (action.getId().equals(selectedOptionId)) {
                    FollowUp resolvedFollowUp = resolveFollowUp(action, context);
                    BranchConfig resolvedBranchConfig = resolveBranchConfig(action, context);

                    return NodeSelectionResult.builder()
                            .nodeType(action.getNodeType())
                            .outputDataType(action.getOutputDataType())
                            .followUp(resolvedFollowUp)
                            .branchConfig(resolvedBranchConfig)
                            .build();
                }
            }
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "선택지 '" + selectedOptionId + "'을(를) 데이터 타입 '" + dataType + "'에서 찾을 수 없습니다.");
    }

    /**
     * followUp의 options_source가 있으면 동적 옵션을 resolve하여 완성된 FollowUp을 반환한다.
     */
    private FollowUp resolveFollowUp(Action action, Map<String, Object> context) {
        FollowUp followUp = action.getFollowUp();
        if (followUp == null) {
            return null;
        }
        if (followUp.getOptionsSource() == null) {
            return followUp;
        }

        List<Option> resolvedOptions = resolveOptionsBySource(followUp.getOptionsSource(), context);
        if (resolvedOptions.isEmpty()) {
            return followUp;
        }

        return FollowUp.builder()
                .question(followUp.getQuestion())
                .options(resolvedOptions)
                .optionsSource(followUp.getOptionsSource())
                .multiSelect(followUp.getMultiSelect())
                .description(followUp.getDescription())
                .build();
    }

    /**
     * branchConfig의 options_source가 있으면 동적 옵션을 resolve하여 완성된 BranchConfig을 반환한다.
     */
    private BranchConfig resolveBranchConfig(Action action, Map<String, Object> context) {
        BranchConfig branchConfig = action.getBranchConfig();
        if (branchConfig == null) {
            return null;
        }
        if (branchConfig.getOptionsSource() == null) {
            return branchConfig;
        }

        List<Option> resolvedOptions = resolveOptionsBySource(branchConfig.getOptionsSource(), context);
        if (resolvedOptions.isEmpty()) {
            return branchConfig;
        }

        return BranchConfig.builder()
                .question(branchConfig.getQuestion())
                .options(resolvedOptions)
                .optionsSource(branchConfig.getOptionsSource())
                .multiSelect(branchConfig.getMultiSelect())
                .description(branchConfig.getDescription())
                .build();
    }

    /**
     * options_source 문자열과 context를 기반으로 동적 옵션을 resolve한다.
     */
    private List<Option> resolveOptionsBySource(String optionsSource, Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }

        if ("fields_from_service".equals(optionsSource)) {
            String serviceName = (String) context.get("service");
            if (serviceName != null) {
                return getServiceFields(serviceName);
            }
        }

        if ("fields_from_data".equals(optionsSource)) {
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) context.get("fields");
            if (fields != null) {
                return fields.stream()
                        .map(field -> Option.builder()
                                .id(field)
                                .label(field)
                                .build())
                        .toList();
            }
        }

        return List.of();
    }

    /**
     * 서비스별 필드 목록 반환 (options_source가 "fields_from_service"일 때 사용).
     */
    public List<Option> getServiceFields(String serviceName) {
        Map<String, List<String>> serviceFields = mappingRules.getServiceFields();
        if (serviceFields == null || !serviceFields.containsKey(serviceName)) {
            return List.of();
        }

        return serviceFields.get(serviceName).stream()
                .map(field -> Option.builder()
                        .id(field)
                        .label(field)
                        .build())
                .toList();
    }

    /**
     * 동적 선택지 생성 — options_source가 "fields_from_data" 또는 "fields_from_service"일 때
     * 런타임 데이터에서 선택지를 생성한다.
     */
    public List<Option> resolveOptionsSource(Action action, Map<String, Object> context) {
        FollowUp followUp = action.getFollowUp();
        BranchConfig branchConfig = action.getBranchConfig();

        String optionsSource = null;
        if (followUp != null && followUp.getOptionsSource() != null) {
            optionsSource = followUp.getOptionsSource();
        } else if (branchConfig != null && branchConfig.getOptionsSource() != null) {
            optionsSource = branchConfig.getOptionsSource();
        }

        if (optionsSource == null) {
            return List.of();
        }

        if ("fields_from_service".equals(optionsSource) && context != null) {
            String serviceName = (String) context.get("service");
            if (serviceName != null) {
                return getServiceFields(serviceName);
            }
        }

        if ("fields_from_data".equals(optionsSource) && context != null) {
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) context.get("fields");
            if (fields != null) {
                return fields.stream()
                        .map(field -> Option.builder()
                                .id(field)
                                .label(field)
                                .build())
                        .toList();
            }
        }

        return List.of();
    }

    /**
     * applicable_when 조건 매칭으로 선택지 필터링.
     */
    private List<Action> filterByApplicableWhen(List<Action> actions, Map<String, Object> context) {
        if (actions == null) {
            return List.of();
        }

        List<Action> filtered = new ArrayList<>();
        for (Action action : actions) {
            if (action.getApplicableWhen() == null || action.getApplicableWhen().isEmpty()) {
                filtered.add(action);
                continue;
            }

            if (context != null && matchesConditions(action.getApplicableWhen(), context)) {
                filtered.add(action);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesConditions(Map<String, Object> conditions, Map<String, Object> context) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object conditionValue = entry.getValue();
            Object contextValue = context.get(key);

            if (contextValue == null) {
                return false;
            }

            if (conditionValue instanceof List<?> conditionList) {
                if (contextValue instanceof List<?> contextList) {
                    if (contextList.stream().noneMatch(conditionList::contains)) {
                        return false;
                    }
                } else if (!conditionList.contains(contextValue)) {
                    return false;
                }
            } else if (!conditionValue.equals(contextValue)) {
                return false;
            }
        }
        return true;
    }

    public MappingRules getMappingRules() {
        return mappingRules;
    }

    private DataTypeConfig getDataTypeConfig(String dataType) {
        DataTypeConfig config = mappingRules.getDataTypes().get(dataType);
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "알 수 없는 데이터 타입: " + dataType);
        }
        return config;
    }
}
