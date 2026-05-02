package org.github.flowify.catalog.dto.picker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoogleDriveFolderRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String parentId;
}
