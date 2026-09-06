package com.imagegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Maps between image file extensions and MIME types. */
public final class Mime {

    private Mime() {
    }

    /** MIME type to declare when uploading {@code path} as an input image. */
    public static String ofInput(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            default -> probe(path, ext);
        };
    }

    /** File extension to use for an output image of the given MIME type. */
    public static String extensionOf(String mimeType) {
        if (mimeType == null) {
            return "png";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private static String probe(Path path, String ext) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && probed.startsWith("image/")) {
                return probed;
            }
        } catch (Exception ignored) {
            // Fall through to the explicit error below.
        }
        throw CliException.usage(
                "Cannot determine an image MIME type for " + path
                        + (ext.isEmpty() ? " (no file extension)" : " (unrecognised extension '." + ext + "')"),
                "Supported inputs: .png, .jpg, .jpeg, .webp, .gif, .heic, .heif");
    }
}
