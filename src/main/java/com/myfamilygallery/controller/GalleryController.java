package com.myfamilygallery.controller;

import com.myfamilygallery.model.Asset;
import com.myfamilygallery.model.ImportRequest;
import com.myfamilygallery.model.ImportResult;
import com.myfamilygallery.service.GalleryService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GalleryController {
    private final GalleryService gallery;

    public GalleryController(GalleryService gallery) {
        this.gallery = gallery;
    }

    @GetMapping("/assets")
    public AssetPage assets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String orientation,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0) throw new IllegalArgumentException("Page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Page size must be between 1 and 100");

        String search = q == null ? null : q.toLowerCase();
        List<Asset> filtered = gallery.list().stream()
                .filter(asset -> type == null || type.isBlank() || type.equalsIgnoreCase(asset.getType()))
                .filter(asset -> orientation == null || orientation.isBlank() ||
                        orientation.equalsIgnoreCase(asset.getOrientation()))
                .filter(asset -> location == null || location.isBlank() ||
                        (asset.getLocation() != null && asset.getLocation().toLowerCase().contains(location.toLowerCase())))
                .filter(asset -> year == null || year.isBlank() || startsWithPart(asset.getTakenAt(), year, 0))
                .filter(asset -> month == null || month.isBlank() || startsWithPart(asset.getTakenAt(), year, month, 1))
                .filter(asset -> search == null || search.isBlank() ||
                        contains(asset.getOriginalName(), search) ||
                        contains(asset.getLocation(), search) ||
                        contains(asset.getDeviceId(), search))
                .sorted(Comparator.comparing(Asset::getTakenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int start = (int) Math.min((long) page * size, filtered.size());
        int end = (int) Math.min((long) start + size, filtered.size());
        int totalPages = (int) ((filtered.size() + (long) size - 1) / size);
        return new AssetPage(filtered.subList(start, end), page, size, filtered.size(), totalPages);
    }

    public record AssetPage(List<Asset> content, int page, int size, long totalElements, int totalPages) {}

    private boolean startsWithPart(String takenAt, String year, int monthIndex) {
        if (takenAt == null) return false;
        return takenAt.startsWith(year);
    }

    private boolean startsWithPart(String takenAt, String year, String month, int ignored) {
        if (takenAt == null) return false;
        if (year != null && !year.isBlank() && !takenAt.startsWith(year)) return false;
        if (month.length() == 1) month = "0" + month;
        if (year == null || year.isBlank()) return takenAt.length() >= 7 && takenAt.substring(5, 7).equals(month);
        return takenAt.length() >= 7 && takenAt.substring(5, 7).equals(month);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<Asset> get(@PathVariable String id) {
        Asset asset = gallery.get(id);
        return asset == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(asset);
    }

    @GetMapping("/assets/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable String id) throws Exception {
        Asset asset = gallery.get(id);
        if (asset == null) return ResponseEntity.notFound().build();
        Resource resource = gallery.content(asset);
        if (resource == null) return ResponseEntity.notFound().build();
        String contentType = Files.probeContentType(resource.getFile().toPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(resource);
    }

    @PostMapping("/import/folder")
    public ImportResult importFolder(@RequestBody ImportRequest request) throws Exception {
        return gallery.importFolder(request.folder());
    }

    @PostMapping(value = "/import/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Asset> importFiles(@RequestParam("files") List<MultipartFile> files) throws Exception {
        java.util.ArrayList<Asset> imported = new java.util.ArrayList<>();
        for (MultipartFile file : files) imported.add(gallery.importUpload(file.getInputStream(), file.getOriginalFilename()));
        return imported;
    }

    @GetMapping("/storage")
    public java.util.Map<String, String> storage() {
        return java.util.Map.of("path", gallery.getStorage().toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
