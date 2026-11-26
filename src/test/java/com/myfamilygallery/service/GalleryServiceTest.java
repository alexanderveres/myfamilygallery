package com.myfamilygallery.service;

import com.myfamilygallery.model.Asset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsTimestampIdsInYearAndMonthFoldersAndAvoidsCollisions() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Instant modifiedAt = Instant.parse("2024-06-03T12:00:00Z");
        for (String fileName : Set.of("first.jpg", "second.jpg")) {
            Path file = source.resolve(fileName);
            Files.write(file, fileName.getBytes());
            Files.setLastModifiedTime(file, FileTime.from(modifiedAt));
        }

        Path storage = tempDir.resolve("storage");
        GalleryService gallery = new GalleryService(storage.toString());
        gallery.importFolder(source.toString());

        LocalDateTime captureDateTime = LocalDateTime.ofInstant(modifiedAt, ZoneId.systemDefault());
        String timestamp = captureDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String year = captureDateTime.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = captureDateTime.format(DateTimeFormatter.ofPattern("MM"));
        Set<String> ids = gallery.list().stream().map(Asset::getId).collect(Collectors.toSet());

        assertEquals(Set.of(timestamp, timestamp + "_1"), ids);
        for (Asset asset : gallery.list()) {
            assertEquals("assets/" + year + "/" + month + "/" + asset.getId() + ".jpg",
                    asset.getRelativePath());
            assertTrue(Files.isRegularFile(storage.resolve(asset.getRelativePath())));
        }
    }
}
