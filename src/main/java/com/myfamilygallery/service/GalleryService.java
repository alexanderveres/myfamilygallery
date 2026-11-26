package com.myfamilygallery.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myfamilygallery.model.Asset;
import com.myfamilygallery.model.ImportResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Service
public class GalleryService {
    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff",
            "mp4", "mov", "m4v", "avi", "mkv", "wmv", "mpg", "mpeg", "webm", "3gp");
    private static final DateTimeFormatter ASSET_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private final Path storage;
    private final Path assetsRoot;
    private final Path indexFile;
    private final ObjectMapper mapper;
    private final List<Asset> assets = Collections.synchronizedList(new ArrayList<>());

    public GalleryService(@Value("${gallery.storage:data/gallery}") String storagePath) throws IOException {
        storage = Paths.get(storagePath).toAbsolutePath().normalize();
        assetsRoot = storage.resolve("assets");
        indexFile = storage.resolve("assets.json");
        Files.createDirectories(assetsRoot);
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    private void load() throws IOException {
        if (!Files.exists(indexFile)) return;
        try (InputStream in = Files.newInputStream(indexFile)) {
            assets.addAll(mapper.readValue(in, new TypeReference<List<Asset>>() {}));
        }
    }

    private synchronized void save() throws IOException {
        Path temp = storage.resolve("assets.json.tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            mapper.writeValue(writer, assets);
        }
        Files.move(temp, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized List<Asset> list() {
        return new ArrayList<>(assets);
    }

    public synchronized Asset get(String id) {
        return assets.stream().filter(asset -> asset.getId().equals(id)).findFirst().orElse(null);
    }

    public synchronized ImportResult importFolder(String folder) throws IOException {
        if (folder == null || folder.isBlank()) throw new IllegalArgumentException("A folder path is required");
        Path source = Paths.get(folder).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) throw new IllegalArgumentException("Folder does not exist or is not a directory");
        int imported = 0, duplicates = 0, skipped = 0;
        try (Stream<Path> paths = Files.walk(source)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(path -> MEDIA_EXTENSIONS.contains(extension(path).toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path file : files) {
                try {
                    String hash = sha256(file);
                    if (assets.stream().anyMatch(asset -> hash.equals(asset.getSha256()))) {
                        duplicates++;
                        continue;
                    }
                    Asset asset = createAsset(file, hash);
                    assets.add(asset);
                    imported++;
                } catch (Exception ex) {
                    skipped++;
                }
            }
        }
        save();
        return new ImportResult(imported, duplicates, skipped,
                imported == 0 ? "No new assets found" : "Imported " + imported + " asset(s)");
    }

    public synchronized Asset importUpload(InputStream input, String originalName) throws Exception {
        String safeName = Paths.get(originalName).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path staged = Files.createTempFile("myfamilygallery-", "-" + safeName);
        Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
        try {
            String hash = sha256(staged);
            Asset existing = assets.stream().filter(asset -> hash.equals(asset.getSha256())).findFirst().orElse(null);
            if (existing != null) return existing;
            Asset asset = createAsset(staged, hash, safeName);
            assets.add(asset);
            save();
            return asset;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private Asset createAsset(Path file, String hash) throws IOException {
        return createAsset(file, hash, file.getFileName().toString());
    }

    private Asset createAsset(Path file, String hash, String originalName) throws IOException {
        String extension = extension(Paths.get(originalName));
        Instant captureTime = captureTime(file);
        LocalDateTime captureDateTime = LocalDateTime.ofInstant(captureTime, ZoneId.systemDefault());
        String year = captureDateTime.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = captureDateTime.format(DateTimeFormatter.ofPattern("MM"));
        Path destinationDir = assetsRoot.resolve(year).resolve(month);
        Files.createDirectories(destinationDir);
        String id = uniqueId(captureDateTime, destinationDir, extension);
        Path destination = destinationDir.resolve(id + (extension.isEmpty() ? "" : "." + extension));
        Files.copy(file, destination);

        Asset asset = new Asset();
        asset.setId(id);
        asset.setOriginalName(originalName);
        asset.setRelativePath(storage.relativize(destination).toString().replace('\\', '/'));
        asset.setType(isImage(originalName) ? "image" : "video");
        asset.setSize(Files.size(destination));
        asset.setSha256(hash);
        asset.setImportedAt(Instant.now().toString());

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(destination.toFile());
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null) {
                Date date = exif.getDateOriginal();
                if (date != null) {
                    asset.setTakenAt(date.toInstant().toString());
                }
            }
            if (asset.getTakenAt() == null) {
                asset.setTakenAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(destination).toInstant(),
                        ZoneId.systemDefault()).toString());
            }
            if (ifd0 != null) {
                asset.setDeviceId(first(ifd0.getDescription(ExifIFD0Directory.TAG_MAKE),
                        ifd0.getDescription(ExifIFD0Directory.TAG_MODEL)));
            }
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                GeoLocation geo = gps.getGeoLocation();
                if (geo != null) asset.setLocation(String.format(Locale.ROOT, "%.6f, %.6f",
                        geo.getLatitude(), geo.getLongitude()));
            }
        } catch (Exception ignored) {
            asset.setTakenAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(destination).toInstant(),
                    ZoneId.systemDefault()).toString());
        }
        setDimensions(asset, destination);
        return asset;
    }

    private void setDimensions(Asset asset, Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            com.drew.metadata.MetadataException.class.getName();
            com.drew.metadata.jpeg.JpegDirectory jpeg =
                    metadata.getFirstDirectoryOfType(com.drew.metadata.jpeg.JpegDirectory.class);
            if (jpeg != null) {
                asset.setWidth(jpeg.getImageWidth());
                asset.setHeight(jpeg.getImageHeight());
            }
        } catch (Exception ignored) {
        }
        if (asset.getWidth() != null && asset.getHeight() != null) {
            asset.setOrientation(asset.getWidth() >= asset.getHeight() ? "landscape" : "portrait");
        } else {
            asset.setOrientation("unknown");
        }
    }

    private Instant captureTime(Path file) throws IOException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Date date = exif == null ? null : exif.getDateOriginal();
            if (date != null) return date.toInstant();
        } catch (Exception ignored) {
        }
        return Files.getLastModifiedTime(file).toInstant();
    }

    private String uniqueId(LocalDateTime captureDateTime, Path destinationDir, String extension) {
        String base = captureDateTime.format(ASSET_ID_FORMAT);
        String id = base;
        int suffix = 1;
        while (idExists(id, destinationDir, extension)) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    private boolean idExists(String id, Path destinationDir, String extension) {
        if (assets.stream().anyMatch(asset -> id.equals(asset.getId()))) return true;
        String fileName = id + (extension.isEmpty() ? "" : "." + extension);
        return Files.exists(destinationDir.resolve(fileName));
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static boolean isImage(String name) {
        String ext = "." + extension(Paths.get(name)).toLowerCase(Locale.ROOT);
        return Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif", ".tif", ".tiff").contains(ext);
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    public Resource content(Asset asset) {
        Path file = storage.resolve(asset.getRelativePath());
        if (!Files.isRegularFile(file)) return null;
        return new FileSystemResource(file);
    }

    public Path getStorage() {
        return storage;
    }
}
