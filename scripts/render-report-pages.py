"""Render export QA with bundled render_docx.py and a native Word PDF on Windows."""
import importlib.util
import json
import sys
from pathlib import Path

from pdf2image import convert_from_path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root / "output/report-export-v1"
skill = Path("C:/Users/31318/.codex/plugins/cache/openai-primary-runtime/documents/26.1022.11436/skills/documents/render_docx.py")
spec = importlib.util.spec_from_file_location("report_docx_renderer", skill)
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)
manifest = {}

for name in ("standard", "stress"):
    # This runtime has no LibreOffice. Keep the packaged PNG renderer and supply
    # the read-only Word conversion made by render-report-word.ps1.
    word_pdf = out / f"word-{name}.pdf"
    renderer.convert_to_pdf = lambda *args, pdf=word_pdf, **kwargs: (str(pdf), "Native Microsoft Word read-only conversion")
    word_pages = renderer.rasterize(str(out / f"{name}.docx"), str(out / f"word-{name}-pages"), 110 if name == "standard" else 75, False, False)
    pdf_dir = out / f"pdf-{name}-pages"
    pdf_dir.mkdir(exist_ok=True)
    pdf_pages = []
    for i, img in enumerate(convert_from_path(str(out / f"{name}.pdf"), dpi=110 if name == "standard" else 75)):
        target = pdf_dir / f"page-{i + 1}.png"
        img.save(target)
        pdf_pages.append(str(target))
    manifest[name] = {"pdf": len(pdf_pages), "word": len(word_pages)}
    for format_name, pages in (("pdf", pdf_pages), ("word", word_pages)):
        columns = 3 if name == "stress" else min(len(pages), 3)
        width, height = 300, 450
        sheet = Image.new("RGB", (columns * width, ((len(pages) + columns - 1) // columns) * height), "#e9ece7")
        draw = ImageDraw.Draw(sheet)
        for i, file in enumerate(pages):
            image = Image.open(file).convert("RGB")
            image.thumbnail((280, 410))
            x, y = (i % columns) * width + (width - image.width) // 2, (i // columns) * height + 25
            sheet.paste(image, (x, y))
            draw.text((x, y - 18), f"{format_name.upper()} {i + 1}", fill="#203a31")
        sheet.save(out / f"{format_name}-{name}-contact.png")

(out / "render-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(json.dumps(manifest))
