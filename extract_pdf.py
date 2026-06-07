import pymupdf
import sys

def extract_text_from_pdf(pdf_path):
    try:
        doc = pymupdf.open(pdf_path)
        for i, page in enumerate(doc):
            print(f"--- Slide {i+1} ---")
            print(page.get_text())
        doc.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    extract_text_from_pdf("REcipify-Virishennya-problemi-skladnogo-obliku-harchuvannya (1).pdf")
