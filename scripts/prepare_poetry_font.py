"""Build the offline poetry font from verified, locally downloaded upstream files.

Requires fonttools. Input: artifacts/poetry-font-source/{LXGWWenKai-Regular.ttf,OFL.txt}.
Upstream: https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530
The renamed subset covers the complete bundled corpus, guide seeds and attribution.
"""
from pathlib import Path
import hashlib
import json
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
from fontTools import subset, __version__
from fontTools.ttLib import TTFont

SOURCE_HASH = "2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b"
LICENSE_HASH = "1a25e35da1031c6c3436fde545bb9cb5aca954e9873afe510c834b8b79bd21a0"


def main():
    source = ROOT / "artifacts/poetry-font-source"
    original = source / "LXGWWenKai-Regular.ttf"
    license_file = source / "OFL.txt"
    for path, digest in [(original, SOURCE_HASH), (license_file, LICENSE_HASH)]:
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Unexpected upstream file: {path.name}")
    corpus = ROOT / "app/src/main/assets/poetry/quotes.tsv"
    text = "".join("".join(row.split("\t")[1:]) for row in corpus.read_text(encoding="utf-8").splitlines())
    text += (ROOT / "app/src/main/java/com/example/youroom/domain/LeisureContent.kt").read_text(encoding="utf-8")
    codepoints = {ord(c) for c in text if not c.isspace()} | set(range(32, 127)) | {0x3000}
    font = TTFont(original, recalcTimestamp=False)
    # A few source quotations contain upstream private-use characters, which
    # have no defined glyph here. Preserve the corpus; do not invent replacements.
    private = {c for c in codepoints if unicodedata.category(chr(c)) == "Co"}
    codepoints -= private
    missing = codepoints - set(font.getBestCmap())
    if missing:
        raise ValueError("Unsupported characters: " + " ".join(f"U+{c:04X}" for c in sorted(missing)))
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.name_legacy = True
    builder = subset.Subsetter(options=options)
    builder.populate(unicodes=codepoints)
    builder.subset(font)
    # This is an Android font derivative, not a web-only delivery. Rename its
    # family and PostScript identity so the upstream reserved names are not used.
    names = {1: "YOURoom Poetry", 2: "Regular", 3: "YOURoomPoetry-Regular-1",
             4: "YOURoom Poetry Regular", 6: "YOURoomPoetry-Regular",
             16: "YOURoom Poetry", 17: "Regular", 18: "YOURoom Poetry Regular",
             21: "YOURoom Poetry", 22: "Regular"}
    for record in font["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    output = ROOT / "app/src/main/res/font/poetry_regular.ttf"
    output.parent.mkdir(parents=True, exist_ok=True)
    font.save(output)
    bundled = TTFont(output)
    assert codepoints <= set(bundled.getBestCmap())
    notices = ROOT / "app/src/main/assets/licenses/poetry_font"
    notices.mkdir(parents=True, exist_ok=True)
    (notices / "OFL.txt").write_bytes(license_file.read_bytes())
    (notices / "NOTICE.txt").write_text(
        "YOURoom Poetry Regular is a renamed subset of LXGW WenKai Regular v1.530.\n"
        "Copyright 2021-2026 LXGW. Copyright 2020 The Klee Project Authors.\n"
        "Source: https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530\n"
        "Licensed under SIL Open Font License 1.1; full license in OFL.txt.\n"
        "Modifications: subset for bundled poetry and attribution, renamed family/PostScript names.\n"
        "No glyph outline edits. Built with scripts/prepare_poetry_font.py.\n", encoding="utf-8")
    manifest = {"upstream": "https://github.com/lxgw/LxgwWenKai", "tag": "v1.530",
                "input_sha256": SOURCE_HASH, "license_sha256": LICENSE_HASH,
                "fonttools": __version__, "characters": len(codepoints),
                "upstream_private_use_fallback": [f"U+{c:04X}" for c in sorted(private)],
                "output": output.relative_to(ROOT).as_posix(), "bytes": output.stat().st_size,
                "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
                "corpus_sha256": hashlib.sha256(corpus.read_bytes()).hexdigest()}
    manifest_path = ROOT / "docs/content/poetry-corpus/font-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
