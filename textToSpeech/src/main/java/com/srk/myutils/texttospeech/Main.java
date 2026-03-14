package com.srk.myutils.texttospeech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("AWS Profile name:");
            String profile = scanner.nextLine().trim();

            System.out.println("Choose input method:");
            System.out.println("1 - Paste content directly");
            System.out.println("2 - Provide file path");
            String choice = scanner.nextLine().trim();

            String content;
            if ("2".equals(choice)) {
                System.out.println("Enter file path:");
                content = Files.readString(Path.of(scanner.nextLine().trim()));
            } else {
                System.out.println("Paste content (type END on a new line to finish):");
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = scanner.nextLine()) != null) {
                    if (line.trim().equals("END")) break;
                    sb.append(line).append("\n");
                }
                content = sb.toString().trim();
            }

            System.out.println("Enter output file path (e.g. output.mp3):");
            String outputPath = scanner.nextLine().trim();

            TextToSpeechConverter converter = new TextToSpeechConverter(profile);
            Path output = Path.of(outputPath);
            converter.convert(content, output);
            System.out.println("Audio saved to: " + outputPath);

            System.out.println("Playing audio...");
            new ProcessBuilder("afplay", output.toAbsolutePath().toString())
                    .inheritIO()
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
