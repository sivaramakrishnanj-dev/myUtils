#!/usr/bin/env python3
import os
import sys
from pathlib import Path

def merge_files(folder_path, output_file):
    folder = Path(folder_path)
    
    # Get all .txt and .md files sorted by filename
    files = [f for f in folder.iterdir() if f.suffix in ['.txt', '.md']]
    files.sort(key=lambda f: f.name)
    
    # Merge content into output file
    with open(output_file, 'w', encoding='utf-8') as out:
        for file in files:
            with open(file, 'r', encoding='utf-8') as f:
                out.write(f.read())
                out.write('\n')  # Add newline between files
    
    print(f"Merged {len(files)} files into {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python merge_files.py <folder_path> <output_file>")
        sys.exit(1)
    
    merge_files(sys.argv[1], sys.argv[2])
