# Speech-to-Text (AWS Transcribe Streaming)

A minimal Java utility that captures microphone audio and converts it to text in real-time using AWS Transcribe Streaming with British English.

## Features

- Real-time speech-to-text via microphone capture
- Partial results displayed live as you speak
- Final assembled transcript printed and saved to file
- Packaged as a fat JAR for standalone execution
- Can also be used as a library via the `SpeechToTextConverter` class

## Prerequisites

- Java 17+
- Maven 3.8+
- A working microphone accessible to Java's `javax.sound.sampled` API
- An AWS profile configured in `~/.aws/config` (or `~/.aws/credentials`) with permissions to call `transcribe:StartStreamTranscription`

## Build

```bash
mvn clean package
```

This produces a fat JAR at `target/speech-to-text-1.0-SNAPSHOT.jar`.

## Run as Fat JAR

```bash
java -jar target/speech-to-text-1.0-SNAPSHOT.jar
```

The application will prompt for:
1. AWS profile name
2. Output file path (e.g. `transcript.txt`)

It then starts listening on the microphone. Partial transcription results appear in real-time. Press Enter to stop — the final transcript is printed and saved to the output file.

## Use as a Library

```java
SpeechToTextConverter converter = new SpeechToTextConverter("my-aws-profile");
converter.start();
// ... do something, then when ready to stop:
String transcript = converter.stop();
```

`start()` opens the mic and begins streaming to Transcribe. `stop()` closes the mic, waits for the session to finish, and returns the full transcript.

## AWS Transcribe Configuration

| Setting       | Value  |
|---------------|--------|
| Language      | English, British (en-GB) |
| Media encoding| PCM (16-bit signed LE, mono, 16 kHz) |
| Region        | us-east-1 |

## Limitations

- **Microphone access required** — the JVM must have permission to access the system microphone. On macOS, you may need to grant microphone access to your terminal application.
- **Region is hardcoded** to `us-east-1`. Change it in `SpeechToTextConverter` if needed.
- **Language is fixed** to `en-GB` and is not configurable at runtime.
