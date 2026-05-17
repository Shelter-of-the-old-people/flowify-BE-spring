package org.github.flowify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.fastapi.base-url}")
    private String fastapiBaseUrl;

    @Value("${app.fastapi.internal-token}")
    private String internalToken;

    @Bean
    public WebClient fastapiWebClient() {
        return WebClient.builder()
                .baseUrl(fastapiBaseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    @Bean
    public WebClient canvasWebClient(@Value("${app.oauth.canvas-lms.api-url}") String canvasApiUrl) {
        return WebClient.builder()
                .baseUrl(canvasApiUrl)
                .build();
    }

    @Bean
    public WebClient googleDriveWebClient() {
        return WebClient.builder()
                .baseUrl("https://www.googleapis.com/drive/v3")
                .build();
    }

    @Bean
    public WebClient googleSheetsWebClient() {
        return WebClient.builder()
                .baseUrl("https://sheets.googleapis.com/v4/spreadsheets")
                .build();
    }

    @Bean
    public WebClient gmailWebClient() {
        return WebClient.builder()
                .baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .build();
    }

    @Bean
    public WebClient notionWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader("Notion-Version", "2022-06-28")
                .build();
    }

    @Bean
    public WebClient githubWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Bean
    public WebClient webNewsWebClient() {
        return WebClient.builder()
                .baseUrl("https://seboard.site/v1")
                .build();
    }
}
