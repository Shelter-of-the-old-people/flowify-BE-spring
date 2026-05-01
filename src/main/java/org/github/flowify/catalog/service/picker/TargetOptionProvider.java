package org.github.flowify.catalog.service.picker;

import org.github.flowify.catalog.dto.picker.TargetOptionResponse;

public interface TargetOptionProvider {

    String getServiceKey();

    TargetOptionResponse getOptions(String sourceMode, String token, String parentId, String query, String cursor);
}
