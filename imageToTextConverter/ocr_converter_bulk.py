import pytesseract
from pytesseract import Output
from PIL import Image
import argparse
import sys
import statistics
import os
import glob
from datetime import datetime

class ImageToMarkdown:
    """
    A utility to convert images to text with basic Markdown formatting
    based on font size analysis.
    """
    
    def __init__(self, image_path, output_path=None):
        self.image_path = image_path
        self.output_path = output_path
        
        # Validating image existence
        if not os.path.exists(self.image_path):
            print(f"Error: The file {self.image_path} does not exist.")
            # We don't exit here anymore to allow batch processing to continue
            # if one file fails, but we raise an error.
            raise FileNotFoundError(f"{self.image_path} not found")

    def _get_median_height(self, data):
        """
        Calculates the median height of all valid words in the document.
        This serves as the 'baseline' font size.
        """
        heights = []
        n_boxes = len(data['text'])
        
        for i in range(n_boxes):
            # conf (confidence) -1 implies no text found in that block
            if int(data['conf'][i]) > 0:
                text = data['text'][i].strip()
                if text:
                    heights.append(data['height'][i])
        
        if not heights:
            return 0
        
        return statistics.median(heights)

    def convert(self):
        """
        Main logic to extract text and apply markdown headers.
        """
        print(f"Processing {os.path.basename(self.image_path)}...")
        
        try:
            img = Image.open(self.image_path)
            
            # image_to_data returns detailed info (coordinates, height, width, conf)
            data = pytesseract.image_to_data(img, output_type=Output.DICT)
        except pytesseract.TesseractNotFoundError:
            print("Error: Tesseract is not installed or not in your PATH.")
            sys.exit(1)
        except Exception as e:
            print(f"Error processing {self.image_path}: {e}")
            return

        baseline_height = self._get_median_height(data)
        
        n_boxes = len(data['text'])
        lines = {} 
        
        # Group words into lines
        for i in range(n_boxes):
            if int(data['conf'][i]) > 60: 
                text = data['text'][i].strip()
                if not text:
                    continue
                    
                key = (data['block_num'][i], data['par_num'][i], data['line_num'][i])
                if key not in lines:
                    lines[key] = []
                
                lines[key].append({
                    'text': text,
                    'height': data['height'][i]
                })

        sorted_keys = sorted(lines.keys())
        output_lines = []
        
        for key in sorted_keys:
            line_words = lines[key]
            avg_line_height = statistics.mean([w['height'] for w in line_words])
            line_text = " ".join([w['text'] for w in line_words])
            
            # Heuristic for Headings
            if baseline_height > 0:
                if avg_line_height > 1.8 * baseline_height:
                    prefix = "# " 
                elif avg_line_height > 1.3 * baseline_height:
                    prefix = "## " 
                else:
                    prefix = ""
            else:
                prefix = ""
            
            output_lines.append(f"{prefix}{line_text}")

        final_text = "\n\n".join(output_lines)
        self._save_output(final_text)

    def _save_output(self, text):
        """Saves the result to a file."""
        if self.output_path:
            with open(self.output_path, 'w', encoding='utf-8') as f:
                f.write(text)
            print(f"   -> Saved to: {os.path.basename(self.output_path)}")
        else:
            print(text)

def get_sorted_files(input_dir, sort_method):
    """
    Retrieves and sorts PNG files from the directory.
    """
    # Case-insensitive search for PNGs manually to be safe across OS
    all_files = os.listdir(input_dir)
    png_files = [os.path.join(input_dir, f) for f in all_files if f.lower().endswith('.png')]
    
    if not png_files:
        print(f"No PNG files found in {input_dir}")
        sys.exit(0)

    if sort_method == 'time':
        # Sort by modification time (oldest first)
        png_files.sort(key=os.path.getmtime)
    else:
        # Sort by filename (alphabetical)
        png_files.sort()
        
    return png_files

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Batch convert PNG images to Markdown text.")
    
    # Updated arguments for folders
    parser.add_argument("input_dir", help="Path to the input folder containing PNG images")
    parser.add_argument("output_dir", help="Path to the output folder for text files")
    parser.add_argument("--sort", choices=['name', 'time'], default='name', 
                        help="Sort files by 'name' (alphabetical) or 'time' (oldest to newest)")
    
    args = parser.parse_args()
    
    # 1. Validate Input Directory
    if not os.path.isdir(args.input_dir):
        print(f"Error: Input directory '{args.input_dir}' does not exist.")
        sys.exit(1)
        
    # 2. Prepare Output Directory
    if not os.path.exists(args.output_dir):
        try:
            os.makedirs(args.output_dir)
            print(f"Created output directory: {args.output_dir}")
        except OSError as e:
            print(f"Error creating output directory: {e}")
            sys.exit(1)

    # 3. Get Sorted Files
    files_to_process = get_sorted_files(args.input_dir, args.sort)
    print(f"Found {len(files_to_process)} PNG files. Sorting by {args.sort}.")

    # 4. Process Batch
    current_date = datetime.now().strftime("%Y-%m-%d") # Format: 2023-11-29
    
    for i, image_file in enumerate(files_to_process, 1):
        # Generate filename: out_YYYY-MM-DD_01.md
        seq_num = f"{i:02d}"
        filename = f"out_{current_date}_{seq_num}.md"
        output_path = os.path.join(args.output_dir, filename)
        
        try:
            converter = ImageToMarkdown(image_file, output_path)
            converter.convert()
        except Exception as e:
            print(f"Skipping {image_file} due to error: {e}")

    print("\nBatch processing complete.")