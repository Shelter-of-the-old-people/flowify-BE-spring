package org.github.flowify.execution.service;

import org.github.flowify.user.entity.User;
import org.github.flowify.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeContextService {

    private final UserRepository userRepository;

    public RuntimeContextService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, Object> buildForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Map.of();
        }

        return userRepository.findById(userId)
                .map(this::toRuntimeContext)
                .orElse(Map.of());
    }

    private Map<String, Object> toRuntimeContext(User user) {
        Map<String, Object> userProfile = new LinkedHashMap<>();
        putIfHasText(userProfile, "user_id", user.getId());
        putIfHasText(userProfile, "email", user.getEmail());
        putIfHasText(userProfile, "display_name", user.getName());

        if (userProfile.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> runtimeContext = new LinkedHashMap<>();
        runtimeContext.put("user_profile", userProfile);
        return runtimeContext;
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
