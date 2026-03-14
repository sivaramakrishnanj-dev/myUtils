package com.srk.myutils.speechtotext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("AWS Profile name:");
            String profile = scanner.nextLine().trim();

            System.out.println("Enter output file path (e.g. transcript.txt):");
            String outputPath = scanner.nextLine().trim();

            SpeechToTextConverter converter = new SpeechToTextConverter(profile);
            converter.start();
            System.out.println("Listening... Press Enter to stop.\n");

            scanner.nextLine();

            String transcript = converter.stop();
            System.out.println("\n--- Final Transcript ---");
            System.out.println(transcript);

            Files.writeString(Path.of(outputPath), transcript);
            System.out.println("\nTranscript saved to: " + outputPath);
        }
    }
}
