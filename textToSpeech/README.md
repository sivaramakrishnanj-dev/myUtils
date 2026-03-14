# Text-to-Speech (AWS Polly)

A minimal Java utility that converts text content into natural-sounding speech audio using AWS Polly's Generative engine with the British English voice Amy.

## Features

- Accepts text input via console paste or file path
- Automatically chunks long text at sentence boundaries to stay within Polly's 3000-character limit per request
- Outputs MP3 audio
- Plays the generated audio after conversion (macOS)
- Packaged as a fat JAR for standalone execution
- Can also be used as a library by depending on the `TextToSpeechConverter` class directly

## Prerequisites

- Java 17+
- Maven 3.8+
- An AWS profile configured in `~/.aws/config` (or `~/.aws/credentials`) with permissions to call `polly:SynthesizeSpeech`

## Build

```bash
mvn clean package
```

This produces a fat JAR at `target/text-to-speech-1.0-SNAPSHOT.jar`.

## Run as Fat JAR

```bash
java -jar target/text-to-speech-1.0-SNAPSHOT.jar
```

The application will prompt for:
1. AWS profile name
2. Input method — paste content directly or provide a file path
3. Output file path (e.g. `output.mp3`)

When pasting content, type `END` on a new line to mark the end of input.

## Use as a Library

Add the dependency to your project and use `TextToSpeechConverter` directly:

```java
TextToSpeechConverter converter = new TextToSpeechConverter("my-aws-profile");
converter.convert("Hello, this is a test.", Path.of("output.mp3"));
```

## AWS Polly Configuration

| Setting    | Value       |
|------------|-------------|
| Engine     | Generative  |
| Language   | English, British (en-GB) |
| Voice      | Amy         |
| Output     | MP3         |
| Region     | us-east-1   |

## Limitations

- **Audio playback is macOS-only** — the fat JAR uses `afplay` (a macOS built-in command) to play the generated MP3. On Linux or Windows the conversion still works, but playback will fail. When using `TextToSpeechConverter` as a library, this does not apply since playback is only in `Main`.
- **Region is hardcoded** to `us-east-1`. Change it in `TextToSpeechConverter` if your Polly access is in a different region.
- **Voice and engine are fixed** — Amy, Generative, en-GB. These are not configurable at runtime currently.
