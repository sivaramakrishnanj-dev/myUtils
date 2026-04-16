package com.srk.myutils.ocr;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class TextractOcr implements AutoCloseable {

    private final TextractClient client;

    public TextractOcr(String profileName) {
        this.client = TextractClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();
    }

    public String extractText(Path imagePath) throws IOException {
        var bytes = SdkBytes.fromByteArray(Files.readAllBytes(imagePath));
        var response = client.detectDocumentText(req -> req
                .document(doc -> doc.bytes(bytes)));

        return response.blocks().stream()
                .filter(b -> b.blockType() == BlockType.LINE)
                .map(Block::text)
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        client.close();
    }
}
