package com.sigcon.backend.general.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

@Service
public class AvatarStorageService {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy-HHmmss");
    private final Path avatarBasePath;

    public AvatarStorageService(@Value("${app.storage.avatar-dir:uploads/avatars}") String avatarDir) {
        this.avatarBasePath = Path.of(avatarDir).toAbsolutePath().normalize();
    }

    public String saveBase64Avatar(String avatarBase64, String previousAvatarFilename) {
        if (!StringUtils.hasText(avatarBase64)) {
            return previousAvatarFilename;
        }

        String payload = avatarBase64.trim();
        String metadata = null;

        if (payload.contains(",")) {
            int commaIndex = payload.indexOf(',');
            metadata = payload.substring(0, commaIndex);
            payload = payload.substring(commaIndex + 1);
        }

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El formato del avatar en base64 no es valido.");
        }

        String extension = resolveExtension(metadata, decodedBytes);

        try {
            Files.createDirectories(avatarBasePath);
            String filename = buildUniqueFilename(extension);
            Files.write(avatarBasePath.resolve(filename), decodedBytes, StandardOpenOption.CREATE_NEW);
            deleteIfExists(previousAvatarFilename);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("No fue posible guardar el avatar localmente.", e);
        }
    }

    private String buildUniqueFilename(String extension) throws IOException {
        String baseName = LocalDateTime.now().format(FILE_DATE_FORMAT);
        Path path = avatarBasePath.resolve(baseName + extension);
        int attempt = 1;

        while (Files.exists(path)) {
            path = avatarBasePath.resolve(baseName + "-" + attempt + extension);
            attempt++;
        }
        return path.getFileName().toString();
    }

    private void deleteIfExists(String filename) {
        if (!StringUtils.hasText(filename)) {
            return;
        }

        try {
            String safeFilename = Path.of(filename).getFileName().toString();
            Path pathToDelete = avatarBasePath.resolve(safeFilename).normalize();
            if (pathToDelete.startsWith(avatarBasePath)) {
                Files.deleteIfExists(pathToDelete);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private String resolveExtension(String metadata, byte[] decodedBytes) {
        String extensionFromMetadata = resolveExtensionFromMetadata(metadata);
        if (extensionFromMetadata != null) {
            return extensionFromMetadata;
        }

        if (isWebp(decodedBytes)) return ".webp";
        if (isPng(decodedBytes)) return ".png";
        if (isJpeg(decodedBytes)) return ".jpg";
        if (isGif(decodedBytes)) return ".gif";

        return ".png";
    }

    private String resolveExtensionFromMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return null;
        }

        String normalized = metadata.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("data:image/webp")) return ".webp";
        if (normalized.startsWith("data:image/png")) return ".png";
        if (normalized.startsWith("data:image/jpeg") || normalized.startsWith("data:image/jpg")) return ".jpg";
        if (normalized.startsWith("data:image/gif")) return ".gif";

        return null;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isGif(byte[] bytes) {
        return bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a';
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }
}
