package com.imagegen;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/** Reads image dimensions from headers only, without decoding the pixels. */
public final class ImageInfo {

    public record Dimensions(int width, int height) {
    }

    private ImageInfo() {
    }

    /** Dimensions of the encoded image, or {@code null} if they cannot be read. */
    public static Dimensions of(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
