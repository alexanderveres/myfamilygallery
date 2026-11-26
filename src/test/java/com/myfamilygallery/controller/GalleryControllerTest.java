package com.myfamilygallery.controller;

import com.myfamilygallery.model.Asset;
import com.myfamilygallery.service.GalleryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GalleryControllerTest {
    private GalleryController controller;

    @BeforeEach
    void setUp() {
        GalleryService gallery = mock(GalleryService.class);
        when(gallery.list()).thenReturn(List.of(
                asset("1", "image", "2024-01-03T00:00:00"),
                asset("2", "video", "2024-01-02T00:00:00"),
                asset("3", "image", "2024-01-01T00:00:00")));
        controller = new GalleryController(gallery);
    }

    @Test
    void filtersBeforeApplyingPageAndReportsFilteredTotals() {
        GalleryController.AssetPage result = controller.assets(
                "image", null, null, null, null, null, 1, 1);

        assertEquals(List.of("3"), result.content().stream().map(Asset::getId).toList());
        assertEquals(2, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.page());
    }

    @Test
    void rejectsInvalidPageAndSize() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.assets(null, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> controller.assets(null, null, null, null, null, null, 0, 101));
    }

    private Asset asset(String id, String type, String takenAt) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setType(type);
        asset.setTakenAt(takenAt);
        return asset;
    }
}
