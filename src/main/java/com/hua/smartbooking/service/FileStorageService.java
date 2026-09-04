package com.hua.smartbooking.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Saves uploaded room images to the on-disk uploads directory (a Docker volume mounted
 * at /app/uploads, so files survive container rebuilds/redeploys, see docker-compose.yml)
 * and returns a URL path the browser can load them from (served by WebConfig at /uploads/**).
 *
 * @author Stavroula Parsali
 */
@Service
public class FileStorageService {

    private static final Path UPLOAD_ROOT = Paths.get("/app/uploads/rooms");
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    public String saveRoomImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image must be smaller than 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPEG, PNG, WEBP, or GIF images are allowed.");
        }

        Files.createDirectories(UPLOAD_ROOT);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        String storedFilename = UUID.randomUUID() + extension;
        Path destination = UPLOAD_ROOT.resolve(storedFilename).normalize();

        if (!destination.startsWith(UPLOAD_ROOT)) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/rooms/" + storedFilename;
    }
}