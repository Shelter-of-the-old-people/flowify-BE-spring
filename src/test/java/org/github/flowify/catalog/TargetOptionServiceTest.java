package org.github.flowify.catalog;

import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.dto.picker.TargetOptionItem;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.GoogleDriveTargetOptionProvider;
import org.github.flowify.catalog.service.picker.TargetOptionProvider;
import org.github.flowify.catalog.service.picker.TargetOptionService;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetOptionServiceTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private OAuthTokenService oauthTokenService;

    @Mock
    private GoogleDriveTargetOptionProvider googleDriveTargetOptionProvider;

    @Mock
    private TargetOptionProvider targetOptionProvider;

    private TargetOptionService targetOptionService;

    @BeforeEach
    void setUp() {
        targetOptionService = new TargetOptionService(
                catalogService,
                oauthTokenService,
                googleDriveTargetOptionProvider,
                List.of(targetOptionProvider, googleDriveTargetOptionProvider)
        );
    }

    @Test
    void getOptions_skipsOauthLookupForCanvasLms() {
        SourceService canvasService = new SourceService(
                "canvas_lms",
                "Canvas LMS",
                true,
                List.of(new SourceMode(
                        "course_files",
                        "특정 과목 강의자료 전체",
                        "FILE_LIST",
                        "manual",
                        Map.of("type", "course_picker")
                ))
        );
        TargetOptionResponse response = TargetOptionResponse.builder()
                .items(List.of())
                .nextCursor(null)
                .build();

        when(catalogService.findSourceService("canvas_lms")).thenReturn(canvasService);
        when(targetOptionProvider.getServiceKey()).thenReturn("canvas_lms");
        when(targetOptionProvider.getOptions("course_files", null, null, null, null))
                .thenReturn(response);

        TargetOptionResponse result = targetOptionService.getOptions(
                "user-1", "canvas_lms", "course_files", null, null, null);

        assertThat(result).isSameAs(response);
        verify(oauthTokenService, never()).getDecryptedToken("user-1", "canvas_lms");
    }

    @Test
    void createGoogleDriveFolder_usesStoredOauthToken() {
        TargetOptionItem createdFolder = TargetOptionItem.builder()
                .id("folder-123")
                .label("강의자료")
                .description("Google Drive folder")
                .type("folder")
                .build();

        when(oauthTokenService.getDecryptedToken("user-1", "google_drive"))
                .thenReturn("drive-token");
        when(googleDriveTargetOptionProvider.createFolder(
                "drive-token", "parent-1", "강의자료"))
                .thenReturn(createdFolder);

        TargetOptionItem result = targetOptionService.createGoogleDriveFolder(
                "user-1", "강의자료", "parent-1");

        assertThat(result).isSameAs(createdFolder);
        verify(oauthTokenService).getDecryptedToken("user-1", "google_drive");
        verify(googleDriveTargetOptionProvider).createFolder(
                "drive-token", "parent-1", "강의자료");
    }
}
