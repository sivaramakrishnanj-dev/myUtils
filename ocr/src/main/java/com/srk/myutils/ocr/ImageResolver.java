package com.srk.myutils.ocr;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

public class ImageResolver {

    private static final Set<String> EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");

    public static List<Path> resolve(Path input, String sortBy) throws IOException {
        List<Path> images;
        if (Files.isDirectory(input)) {
            try (Stream<Path> stream = Files.list(input)) {
                images = stream.filter(ImageResolver::isImage).toList();
            }
        } else if (Files.isRegularFile(input) && isImage(input)) {
            images = List.of(input);
        } else {
            throw new IllegalArgumentException("Input is not a valid image file or directory: " + input);
        }

        if (images.isEmpty()) {
            throw new IllegalArgumentException("No PNG/JPEG files found in: " + input);
        }

        List<Path> sorted = new ArrayList<>(images);
        if ("name".equalsIgnoreCase(sortBy)) {
            sorted.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        } else {
            sorted.sort(Comparator.comparing(ImageResolver::creationTime));
        }
        return sorted;
    }

    private static boolean isImage(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static long creationTime(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
