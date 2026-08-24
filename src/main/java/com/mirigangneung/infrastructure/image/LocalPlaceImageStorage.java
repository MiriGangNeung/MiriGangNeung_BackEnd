package com.mirigangneung.infrastructure.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class LocalPlaceImageStorage implements PlaceImageStorage {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]+");
    private static final List<String> ORIGINAL_EXTENSIONS = List.of("jpg", "png", "gif", "webp");

    private final Path root;
    private final int thumbnailMaxWidth;

    @Autowired
    public LocalPlaceImageStorage(ImageCacheProperties properties) {
        this(properties.storageDir(), properties.thumbnailMaxWidth());
    }

    public LocalPlaceImageStorage(String root, int thumbnailMaxWidth) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.thumbnailMaxWidth = thumbnailMaxWidth;
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalStateException("이미지 저장 디렉터리를 만들 수 없습니다.", exception);
        }
    }

    @Override
    public StoredImage store(String sourceUrl, byte[] originalBytes, String contentType) throws IOException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IOException("이미지 원본 URL이 비어 있습니다.");
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (!normalizedContentType.startsWith("image/")) {
            throw new IOException("이미지 Content-Type이 아닙니다.");
        }
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) {
            throw new IOException("이미지 파일을 해석할 수 없습니다.");
        }

        String fingerprint = fingerprint(sourceUrl);
        String originalKey = "place-" + fingerprint + "-original." + extension(normalizedContentType);
        String thumbnailKey = "place-" + fingerprint + "-thumbnail.jpg";
        writeAtomically(originalKey, originalBytes);
        byte[] thumbnailBytes = thumbnail(original);
        writeAtomically(thumbnailKey, thumbnailBytes);
        return new StoredImage(originalKey, thumbnailKey, normalizedContentType,
                originalBytes.length, thumbnailBytes.length);
    }

    @Override
    public Optional<StoredImage> find(String sourceUrl) throws IOException {
        String fingerprint = fingerprint(sourceUrl);
        String thumbnailKey = "place-" + fingerprint + "-thumbnail.jpg";
        Path thumbnail = safePath(thumbnailKey);
        if (!Files.isRegularFile(thumbnail)) {
            return Optional.empty();
        }
        for (String extension : ORIGINAL_EXTENSIONS) {
            String originalKey = "place-" + fingerprint + "-original." + extension;
            Path original = safePath(originalKey);
            if (Files.isRegularFile(original)) {
                return Optional.of(new StoredImage(
                        originalKey,
                        thumbnailKey,
                        contentType(extension),
                        Files.size(original),
                        Files.size(thumbnail)));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredAsset> open(String storageKey) throws IOException {
        Path path = safePath(storageKey);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(new StoredAsset(
                Files.newInputStream(path),
                contentType(storageKey),
                Files.size(path)));
    }

    private void writeAtomically(String key, byte[] bytes) throws IOException {
        Path target = safePath(key);
        Path temporary = Files.createTempFile(root, "." + key + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] thumbnail(BufferedImage original) throws IOException {
        int width = Math.min(original.getWidth(), thumbnailMaxWidth);
        int height = Math.max(1, (int) Math.round((double) original.getHeight() * width / original.getWidth()));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(resized, "jpg", output)) {
            throw new IOException("JPEG 썸네일을 만들 수 없습니다.");
        }
        return output.toByteArray();
    }

    private Path safePath(String storageKey) throws IOException {
        if (storageKey == null || !SAFE_KEY.matcher(storageKey).matches()) {
            throw new IOException("안전하지 않은 이미지 storage key입니다.");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("이미지 storage key가 저장소 경계를 벗어납니다.");
        }
        return path;
    }

    private static String fingerprint(String sourceUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sourceUrl.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private static String contentType(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
