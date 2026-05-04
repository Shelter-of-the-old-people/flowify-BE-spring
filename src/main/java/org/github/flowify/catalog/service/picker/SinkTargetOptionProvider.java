package org.github.flowify.catalog.service.picker;

import org.github.flowify.catalog.dto.picker.TargetOptionResponse;

public interface SinkTargetOptionProvider {

    String getServiceKey();

    TargetOptionResponse getOptions(String token, String type, String parentId, String query, String cursor);
}
