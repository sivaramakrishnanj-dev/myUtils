package com.srk.myutils.ocr;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "ocr", description = "Extract text from images using Amazon Textract",
        mixinStandardHelpOptions = true, version = "1.0.0")
public class OcrApp implements Callable<Integer> {

    @Option(names = {"-i", "--input"}, required = true, description = "Image file or folder")
    private Path input;

    @Option(names = {"-o", "--output"}, required = true, description = "Output folder")
    private Path output;

    @Option(names = {"-p", "--profile"}, required = true, description = "AWS profile name")
    private String profile;

    @Option(names = {"-s", "--sort"}, defaultValue = "time", description = "Sort by: name or time (default: time)")
    private String sort;

    @Override
    public Integer call() throws Exception {
        List<Path> images = ImageResolver.resolve(input, sort);
        Files.createDirectories(output);

        StringBuilder merged = new StringBuilder();
        try (var ocr = new TextractOcr(profile)) {
            for (int i = 0; i < images.size(); i++) {
                Path img = images.get(i);
                String name = img.getFileName().toString();
                System.out.printf("Processing %d/%d: %s%n", i + 1, images.size(), name);

                String text;
                try {
                    text = ocr.extractText(img);
                } catch (Exception e) {
                    System.err.printf("FAILED: %s — %s%n", name, e.getMessage());
                    text = "[OCR FAILED: " + name + "]";
                }

                // Write individual file
                String outName = String.format("%03d_%s.txt", i + 1, stripExtension(name));
                Files.writeString(output.resolve(outName), text);

                if (i > 0) merged.append("\n\n");
                merged.append(text);
            }
        }

        Path mergedFile = output.resolve("merged.txt");
        Files.writeString(mergedFile, merged.toString());
        System.out.println(mergedFile.toAbsolutePath());
        return 0;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new OcrApp()).execute(args));
    }
}
