# Image to Text Converter

A Python toolkit that converts images (screenshots, scans, photos) into clean Markdown-formatted text using OCR. It intelligently detects headings vs body text by analyzing font sizes, so the output preserves document structure — not just raw text.

## How It Works

The converter uses Tesseract OCR with a font-size heuristic:
- Extracts every word along with its pixel height using `pytesseract.image_to_data`
- Calculates the **median height** across all words to establish a "body text" baseline
- Lines significantly larger than the baseline get Markdown heading prefixes (`#` for ~1.8x, `##` for ~1.3x)
- Words are grouped into lines and reassembled into readable Markdown

## Use Cases

- **Book chapter digitization** — Screenshot pages of a book, convert them into a single Markdown chapter
- **Presentation slides** — Extract text from slide screenshots while preserving title/body hierarchy
- **Scanned documents** — Convert scanned PDFs or photos of printed documents into editable text
- **Whiteboard captures** — Pull text from photos of whiteboards or handwritten notes

## Setup

### Prerequisites

- Python 3.x
- [Tesseract OCR engine](https://github.com/tesseract-ocr/tesseract) installed and available in PATH
  ```bash
  # macOS
  brew install tesseract

  # Ubuntu/Debian
  sudo apt install tesseract-ocr
  ```

### Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install pytesseract Pillow
```

## Usage

> Always activate the virtual environment first: `source .venv/bin/activate`

### 1. Single Image

```bash
python ocr_converter.py <image_path> -o <output_file>
```

```bash
python ocr_converter.py photo.png -o result.md
```

### 2. Batch Convert (Folder of Images)

Processes all PNG files in a folder and outputs numbered Markdown files.

```bash
python ocr_converter_bulk.py <input_dir> <output_dir> [--sort name|time]
```

```bash
# Sort by filename (default)
python ocr_converter_bulk.py ./data/input ./data/output

# Sort by file modification time (oldest first) — useful for screenshots taken in sequence
python ocr_converter_bulk.py ./data/input ./data/output --sort time
```

### 3. Merge Output Files

Combines all individual `.md`/`.txt` files from a folder into a single file, sorted by filename.

```bash
python merge_files.py <folder_path> <output_file>
```

```bash
python merge_files.py ./data/output ./data/merged_output/chapter.md
```

## Example: Book Chapter Digitization

A full pipeline to convert screenshots of a book chapter into a single Markdown document:

```bash
source .venv/bin/activate

# Step 1: Place your page screenshots in data/input/

# Step 2: Batch convert (sort by time so pages stay in order)
python ocr_converter_bulk.py ./data/input ./data/output --sort time

# Step 3: Merge into one chapter file
python merge_files.py ./data/output ./data/merged_output/my_chapter.md

deactivate
```

## Project Structure

```
imageToTextConverter/
├── ocr_converter.py          # Single image → Markdown
├── ocr_converter_bulk.py     # Batch: folder of images → numbered Markdown files
├── merge_files.py            # Merge multiple text/md files into one
├── data/
│   ├── input/                # Drop your source images here
│   ├── output/               # Individual converted files land here
│   └── merged_output/        # Final merged documents
└── .venv/                    # Python virtual environment
```

## Tips

- For book screenshots, **sort by time** (`--sort time`) works well since screenshots are typically taken in page order
- The heading detection is heuristic-based — it works best on cleanly printed text with clear size differences between headings and body
- Low-confidence OCR results (below 60%) are automatically filtered out to reduce noise
- Output files are named `out_YYYY-MM-DD_NN.md` so batch runs on different days don't collide
