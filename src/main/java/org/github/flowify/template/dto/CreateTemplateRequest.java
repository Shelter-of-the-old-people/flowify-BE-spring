package org.github.flowify.template.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateTemplateRequest {

    @NotBlank(message = "워크플로우 ID는 필수입니다.")
    private String workflowId;

    @NotBlank(message = "템플릿 이름은 필수입니다.")
    private String name;

    private String description;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    private String folderKey;

    private String icon;
}
