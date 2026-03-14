import pytesseract
from pytesseract import Output
from PIL import Image
import argparse
import sys
import statistics
import os

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
            sys.exit(1)

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
        print(f"Processing {self.image_path}...")
        
        try:
            img = Image.open(self.image_path)
            
            # image_to_data returns detailed info (coordinates, height, width, conf)
            # We use this to detect font sizes.
            data = pytesseract.image_to_data(img, output_type=Output.DICT)
        except pytesseract.TesseractNotFoundError:
            print("Error: Tesseract is not installed or not in your PATH.")
            print("Please install Tesseract OCR engine (not just the python wrapper).")
            sys.exit(1)
        except Exception as e:
            print(f"An unexpected error occurred: {e}")
            sys.exit(1)

        baseline_height = self._get_median_height(data)
        print(f"Detected baseline font height: {baseline_height}px")

        n_boxes = len(data['text'])
        lines = {} # Key: (block_num, para_num, line_num), Value: List of words/metadata
        
        # Group words into lines
        for i in range(n_boxes):
            if int(data['conf'][i]) > 60: # Filter low confidence noise
                text = data['text'][i].strip()
                if not text:
                    continue
                    
                key = (data['block_num'][i], data['par_num'][i], data['line_num'][i])
                if key not in lines:
                    lines[key] = []
                
                lines[key].append({
                    'text': text,
                    'height': data['height'][i],
                    'left': data['left'][i]
                })

        # Sort lines by vertical position (block/para/line numbers usually handle this, 
        # but dictionaries are unordered in older python versions, though keys logic holds)
        sorted_keys = sorted(lines.keys())
        
        output_lines = []
        
        for key in sorted_keys:
            line_words = lines[key]
            
            # Calculate average height of this specific line
            avg_line_height = statistics.mean([w['height'] for w in line_words])
            
            # Reconstruct the string for the line
            line_text = " ".join([w['text'] for w in line_words])
            
            # Heuristic for Headings
            # If line is > 1.5x larger than baseline, it's likely a Header
            if baseline_height > 0:
                if avg_line_height > 1.8 * baseline_height:
                    prefix = "# " # H1
                elif avg_line_height > 1.3 * baseline_height:
                    prefix = "## " # H2
                else:
                    prefix = ""
            else:
                prefix = ""
            
            output_lines.append(f"{prefix}{line_text}")

        final_text = "\n\n".join(output_lines)
        
        self._save_output(final_text)

    def _save_output(self, text):
        """Saves the result to a file or prints to console."""
        if self.output_path:
            with open(self.output_path, 'w', encoding='utf-8') as f:
                f.write(text)
            print(f"Success! Text extracted to: {self.output_path}")
        else:
            print("\n--- Extracted Text ---\n")
            print(text)
            print("\n----------------------\n")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Extract text from images to Markdown.")
    parser.add_argument("input", help="Path to the input image file")
    parser.add_argument("-o", "--output", help="Path to the output text file", default="output.md")
    
    args = parser.parse_args()
    
    converter = ImageToMarkdown(args.input, args.output)
    converter.convert()