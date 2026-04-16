# OCR — Book Page Text Extractor

Extract text from book page screenshots using Amazon Textract.

## Build

```bash
mvn clean package
```

## Usage

```bash
java -jar target/ocr-1.0.0.jar \
  --input /path/to/screenshots \
  --output /path/to/output \
  --profile your-aws-profile \
  --sort time
```

### Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `-i, --input` | Yes | — | Image file or folder containing PNG/JPEG files |
| `-o, --output` | Yes | — | Output folder for extracted text files |
| `-p, --profile` | Yes | — | AWS credentials profile name |
| `-s, --sort` | No | `time` | Sort images by `time` (creation time) or `name` |

### Output

- `001_filename.txt`, `002_filename.txt`, ... — individual page text files
- `merged.txt` — all pages merged in order

The absolute path of `merged.txt` is printed to stdout on completion.

## Prerequisites

- Java 21+
- AWS profile with Textract access (`textract:DetectDocumentText`)
- Region: us-east-1
